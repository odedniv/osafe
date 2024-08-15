package encryption

import (
	"errors"
	"fmt"
)

type Message struct {
	Keys    []Key   `json:"keys"`
	Content Content `json:"content"`
}

func (m *Message) WithContent(content Content) Message {
	return Message{
		Keys:    m.Keys,
		Content: content,
	}
}

func (m *Message) Decrypt(key Key, keyValue []byte) (DecryptedMessage, error) {
	baseKey, err := key.Content.Decrypt(keyValue)
	if err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed decrypting key: %v", err)
	}
	c, err := m.Content.Decrypt(baseKey)
	if err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed decrypting content: %v", err)
	}
	return DecryptedMessage{
		Message: *m,
		baseKey: baseKey,
		Content: c,
	}, nil
}

func (m *Message) DecryptPassphrase(passphrase []byte) (DecryptedMessage, error) {
	var errs []error
	for _, key := range m.Keys {
		passphraseKey, ok := key.Label.Value.(*KeyLabelPassphrase)
		if !ok {
			continue
		}

		digest, err := passphraseKey.Digest(passphrase)
		if err != nil {
			errs = append(errs, fmt.Errorf("failed digest: %v", err))
		}

		decrypted, err := m.Decrypt(key, digest)
		if err != nil {
			errs = append(errs, fmt.Errorf("failed decrypt: %v", err))
			continue
		}
		return decrypted, nil
	}
	if err := errors.Join(errs...); err != nil {
		return DecryptedMessage{}, fmt.Errorf("failed decrypting passphrase: %v", err)
	}
	return DecryptedMessage{}, nil
}
