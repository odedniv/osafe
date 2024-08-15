package encryption

import (
	"crypto/rand"
	"fmt"
)

var baseKeySize = 64

type DecryptedMessage struct {
	Message Message
	baseKey []byte
	Content []byte
}

func NewDecryptedMessage(passphrase []byte) (DecryptedMessage, error) {
	baseKey := make([]byte, baseKeySize)
	if _, err := rand.Read(baseKey); err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed generating random base key: %v", err)
	}
	c, err := EncryptContent(baseKey, nil)
	if err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed encrypting content: %v", err)
	}

	kl := NewKeyLabelPassphrase()
	kv, err := kl.Digest(passphrase)
	if err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed digesting key: %v", err)
	}

	kc, err := EncryptContent(baseKey, kv)
	if err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed encrypting key: %v", err)
	}

	return DecryptedMessage{
		Message: Message{
			Keys:    []Key{{Label: KeyLabel{&kl}, Content: kc}},
			Content: c,
		},
		baseKey: baseKey,
		Content: nil,
	}, nil
}

func (dm *DecryptedMessage) WithContent(content []byte) (DecryptedMessage, error) {
	encrypted, err := EncryptContent(dm.baseKey, content)
	if err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed encrypting content: %v", err)
	}
	return DecryptedMessage{
		Message: dm.Message.WithContent(encrypted),
		baseKey: dm.baseKey,
		Content: content,
	}, nil
}
