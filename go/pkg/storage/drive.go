package storage

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
	"google.golang.org/api/drive/v3"
	"google.golang.org/api/option"
)

//go:embed google-oauth.json
var googleOauthConfig []byte
var googleTokenFilePath = path.Join(".osafe", "google-creds.json") // Relative to os.UserHomeDir.

type driveStorage struct {
	srv *drive.Service
}

type driveFileMetadata struct {
	fileId       string
	modifiedTime time.Time
}

func (s *driveStorage) read() (content, error) {
	if err := s.prepare(); err != nil {
		return content{}, fmt.Errorf("failed preparing drive storage for read: %v", err)
	}
	// Query
	m, err := s.query()
	if err != nil {
		return content{}, fmt.Errorf("failed querying drive storage for read: %v", err)
	} else if m.fileId == "" {
		return content{}, nil
	}
	// Downloading
	r, err := s.srv.
		Files.
		Get(m.fileId).
		Download()
	if err != nil {
		return content{}, fmt.Errorf("failed downloading from drive storage: %v", err)
	}
	defer r.Body.Close()

	bytes, err := io.ReadAll(r.Body)
	if err != nil {
		return content{}, fmt.Errorf("failed reading from drive storage: %v", err)
	}
	return content{bytes, m.modifiedTime}, nil
}

func (s *driveStorage) write(c content) error {
	if err := s.prepare(); err != nil {
		return fmt.Errorf("failed preparing drive storage for write: %v", err)
	}
	// Query
	m, err := s.query()
	if err != nil {
		return fmt.Errorf("failed querying drive storage for write: %v", err)
	}
	// Create or update
	if m.fileId == "" {
		err = s.create(c)
	} else {
		err = s.update(c, m.fileId)
	}
	if err != nil {
		return fmt.Errorf("failed create or update drive storage: %v", err)
	}
	return nil
}

func (s *driveStorage) create(c content) error {
	_, err := s.srv.
		Files.
		Create(
			&drive.File{
				Name:         storageFilename,
				ModifiedTime: c.modifiedTime.Format(time.RFC3339),
			}).
		Media(bytes.NewReader(c.bytes)).
		Do()
	if err != nil {
		return fmt.Errorf("failed inserting drive storage: %v", err)
	}
	return nil
}

func (s *driveStorage) update(c content, fileId string) error {
	_, err := s.srv.
		Files.
		Update(
			fileId,
			&drive.File{
				Name:         storageFilename,
				ModifiedTime: c.modifiedTime.Format(time.RFC3339),
			}).
		Media(bytes.NewReader(c.bytes)).
		Do()
	if err != nil {
		return fmt.Errorf("failed updating drive storage: %v", err)
	}
	return nil
}

func (s *driveStorage) query() (driveFileMetadata, error) {
	fileList, err := s.srv.
		Files.
		List().
		Q(fmt.Sprintf("name = '%s' and 'root' in parents and trashed = false", storageFilename)).
		Fields("files(id, modifiedTime)").
		Do()
	if err != nil {
		return driveFileMetadata{}, fmt.Errorf("failed querying drive storage: %v", err)
	}

	switch len(fileList.Files) {
	case 0:
	case 1:
		fileId := fileList.Files[0].Id
		modifiedTime, err := time.Parse(time.RFC3339, fileList.Files[0].ModifiedTime)
		if err != nil {
			return driveFileMetadata{}, fmt.Errorf("failed parsing drive storage modified time: %v", err)
		}
		return driveFileMetadata{fileId, modifiedTime}, nil
	}
	return driveFileMetadata{}, fmt.Errorf("more than one %s file in Drive.", storageFilename)
}

func (s *driveStorage) prepare() error {
	if s.srv != nil {
		return nil // Already prepared.
	}
	// Creating service
	client, err := getDriveClient()
	if err != nil {
		return fmt.Errorf("failed getting drive storage client: %v", err)
	}
	s.srv, err = drive.NewService(context.Background(), option.WithHTTPClient(client))
	if err != nil {
		return fmt.Errorf("failed creating drive storage service: %v", err)
	}
	return nil
}

func getDriveClient() (*http.Client, error) {
	config, err := google.ConfigFromJSON(googleOauthConfig, drive.DriveFileScope)
	if err != nil {
		return nil, fmt.Errorf("failed parsing Google OAuth config: %v", err)
	}

	tok, err := getDriveTokenFromFile()
	if os.IsNotExist(err) {
		tok, err = getDriveTokenFromWeb(config)
	}
	if err != nil {
		return nil, fmt.Errorf("failed getting Google OAuth web token: %v", err)
	}
	return config.Client(context.Background(), tok), nil
}

func getDriveTokenFromFile() (*oauth2.Token, error) {
	name, err := getDriveTokenFileName()
	if err != nil {
		return nil, fmt.Errorf("failed getting Google OAuth token file name for read: %v", err)
	}

	f, err := os.Open(name)
	if err != nil {
		return nil, fmt.Errorf("failed opening Google OAuth token file for read: %v", err)
	}
	defer f.Close()

	var tok oauth2.Token
	err = json.NewDecoder(f).Decode(&tok)
	if err != nil {
		return nil, fmt.Errorf("failed parsing Google OAuth token file: %v", err)
	}
	return &tok, nil
}

func getDriveTokenFromWeb(config *oauth2.Config) (*oauth2.Token, error) {
	authURL := config.AuthCodeURL("state-token", oauth2.AccessTypeOffline)
	fmt.Printf("Go to the following link in your browser then type the "+
		"authorization code: \n%v\n", authURL)
	var authCode string
	if _, err := fmt.Scan(&authCode); err != nil {
		return nil, fmt.Errorf("failed scanning Google OAuth auth code: %v", err)
	}

	tok, err := config.Exchange(context.TODO(), authCode)
	if err != nil {
		return nil, fmt.Errorf("failed exchanging Google OAuth auth code: %v", err)
	}

	err = saveDriveTokenToFile(tok)
	if err != nil {
		return nil, fmt.Errorf("failed saving Google OAuth token file: %v", err)
	}
	return tok, nil
}

func saveDriveTokenToFile(tok *oauth2.Token) error {
	name, err := getDriveTokenFileName()
	if err != nil {
		return fmt.Errorf("failed getting Google OAuth token file name for write: %v", err)
	}

	f, err := os.OpenFile(name, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0600)
	if err != nil {
		return fmt.Errorf("failed opening Google OAuth token file for write: %v", err)
	}
	defer f.Close()

	err = json.NewEncoder(f).Encode(tok)
	if err != nil {
		return fmt.Errorf("failed encoding Google OAuth token file: %v", err)
	}
	return nil
}

func getDriveTokenFileName() (string, error) {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("failed getting user home dir: %v", err)
	}
	name := path.Join(homeDir, googleTokenFilePath)
	return name, nil
}
