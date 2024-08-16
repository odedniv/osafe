package main

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"runtime"
	"syscall"
	"time"

	"github.com/odedniv/osafe/go/pkg/encryption"
	"github.com/odedniv/osafe/go/pkg/storage"
	"golang.org/x/term"
)

var timeout = time.Minute * 5

func main() {
	if err := run(); err != nil {
		panic(err)
	}
}

func run() error {
	// Read
	m, err := storage.Read()
	if err != nil {
		return err
	}
	// Create or decrypt
	var dm encryption.DecryptedMessage
	if m == nil {
		dm, err = create()
	} else {
		dm, err = decrypt(*m)
	}
	if err != nil {
		return err
	}
	// Edit
	c, err := edit(dm.Content)
	if err != nil {
		return err
	}
	if bytes.Equal(dm.Content, c) {
		return nil // No changes
	}
	// Write
	dm, err = dm.WithContent(c)
	if err != nil {
		return err
	}
	err = storage.Write(dm.Message)
	if err != nil {
		return err
	}
	return nil
}

func create() (encryption.DecryptedMessage, error) {
	passphrase, err := readPassphrase()
	if err != nil {
		return encryption.DecryptedMessage{}, err
	}
	return encryption.NewDecryptedMessage(passphrase)
}

func decrypt(m encryption.Message) (encryption.DecryptedMessage, error) {
	for {
		passphrase, err := readPassphrase()
		if err != nil {
			return encryption.DecryptedMessage{}, err
		}

		dm, err := m.DecryptPassphrase(passphrase)
		if err != nil {
			fmt.Println(err)
			continue
		}

		return dm, nil
	}
}

func readPassphrase() (passphrase []byte, err error) {
	fmt.Print("Enter passphrase: ")
	fd := int(os.Stdin.Fd())

	// Restore state after Ctrl+C
	s, err := term.GetState(fd)
	if err != nil {
		return nil, err
	}

	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(c)
	go func() {
		sig := <-c
		err = term.Restore(fd, s)
		signal.Stop(c)
		err = syscall.Kill(syscall.Getpid(), sig.(syscall.Signal))
	}()

	passphrase, err = term.ReadPassword(fd)
	fmt.Println()
	return
}

func edit(content []byte) ([]byte, error) {
	// Creating temp file
	f, err := os.CreateTemp("", "osafe-")
	if err != nil {
		return nil, fmt.Errorf("failed creating temp file: %v", err)
	}
	defer os.Remove(f.Name())
	// Writing to temp file
	_, err = f.Write(content)
	if err != nil {
		return nil, fmt.Errorf("failed writing temp file: %v", err)
	}
	// Starting editor
	if err = startEditor(f.Name()); err != nil {
		return nil, err
	}
	// Reading temp file
	_, err = f.Seek(0, io.SeekStart)
	if err != nil {
		return nil, fmt.Errorf("failed seeking temp file: %v", err)
	}
	r, err := io.ReadAll(f)
	if err != nil {
		return nil, fmt.Errorf("failed reading temp file: %v", err)
	}
	return r, nil
}

func startEditor(file string) error {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	cmd := pipeCmd(exec.CommandContext(ctx, editor(), file))
	if err := cmd.Run(); err != nil {
		err = errors.Join(err, resetTerminal())
		return fmt.Errorf("failed waiting for editor: %v", err)
	}
	return nil
}

func editor() string {
	r := os.Getenv("EDITOR")
	if r == "" {
		fmt.Println("EDITOR environment variable not set.")
		for r == "" {
			fmt.Print("Type your preferred editor: ")
			fmt.Scanln(&r)
		}
	}
	return r
}

func resetTerminal() error {
	var err error
	switch runtime.GOOS {
	case "linux":
		err = pipeCmd(exec.Command("reset")).Run()
		if err != nil {
			err = pipeCmd(exec.Command("clear")).Run()
		}
	case "windows":
		err = pipeCmd(exec.Command("cls")).Run()
	default:
		err = fmt.Errorf("don't know how to reset terminal for: %s", runtime.GOOS)
	}
	if err != nil {
		return fmt.Errorf("failed resetting terminal: %v", err)
	}
	return nil
}

func pipeCmd(cmd *exec.Cmd) *exec.Cmd {
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd
}
