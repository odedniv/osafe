package encryption

import (
	"bytes"
	"crypto/rand"
	"errors"
	"fmt"
	"io"
)

var defaultContentCipherType CipherType = cipherTypes["AES_128"]
var defaultContentDigestType DigestType = digestTypes["SHA_1"]

type Content struct {
	CipherType CipherType `json:"cipherType"`
	DigestType DigestType `json:"digestType"`
	IV         []byte     `json:"iv"`
	Digest     []byte     `json:"digest"`
	Content    []byte     `json:"content"`
}

func EncryptContent(key []byte, content []byte) (Content, error) {
	// IV
	iv := make([]byte, defaultContentCipherType.ivSize())
	if _, err := io.ReadFull(rand.Reader, iv); err != nil {
		return Content{}, fmt.Errorf("failed generating random IV: %v", err)
	}
	// Encrypt
	encrypted, err := defaultContentCipherType.encrypt(key[:defaultContentCipherType.impl.keySize], iv, content)
	if err != nil {
		return Content{}, fmt.Errorf("failed encrypting: %v", err)
	}
	// Digest
	digest, err := defaultContentDigestType.digest(content)
	if err != nil {
		return Content{}, fmt.Errorf("failed digesting for encrypt: %v", err)
	}

	return Content{
		CipherType: defaultContentCipherType,
		DigestType: defaultContentDigestType,
		IV:         iv,
		Digest:     digest,
		Content:    encrypted,
	}, nil
}

func (c *Content) Decrypt(key []byte) ([]byte, error) {
	decrypted, err := c.CipherType.decrypt(key[:c.CipherType.impl.keySize], c.IV, c.Content)
	if err != nil {
		return nil, fmt.Errorf("failed decrypting: %v", err)
	}
	// Digest
	digest, err := c.DigestType.digest(decrypted)
	if err != nil {
		return nil, fmt.Errorf("failed digesting for decrypt: %v", err)
	}

	if !bytes.Equal(digest, c.Digest) {
		return nil, errors.New("failed decrypting")
	}

	return decrypted, nil
}
