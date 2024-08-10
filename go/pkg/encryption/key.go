package encryption

import (
	"encoding"
	"fmt"
	"strings"
)

type Key struct {
	Label   KeyLabel `json:"label"`
	Content Content  `json:"content"`
}

type KeyLabel struct {
	Value KeyLabelImpl
}

// JSON

func (l KeyLabel) MarshalText() ([]byte, error) {
	text, err := l.Value.MarshalText()
	if err != nil {
		return nil, fmt.Errorf("failed marsheling key label: %v", err)
	}
	return []byte(fmt.Sprintf("%s/%s", l.Value.name(), text)), nil
}

func (l *KeyLabel) UnmarshalText(text []byte) error {
	split := strings.SplitN(string(text), "/", 2)
	l.Value = keyLabels[split[0]]()
	if err := l.Value.UnmarshalText([]byte(split[1])); err != nil {
		return fmt.Errorf("failed unmarsheling key label: %v", err)
	}
	return nil
}

// Implementations

type KeyLabelImpl interface {
	name() string
	encoding.TextMarshaler
	encoding.TextUnmarshaler
}

var keyLabels = map[string]func() KeyLabelImpl{
	(&KeyLabelPassphrase{}).name(): func() KeyLabelImpl { return &KeyLabelPassphrase{} },
	(&KeyLabelBiometric{}).name():  func() KeyLabelImpl { return &KeyLabelBiometric{} },
}

// Passphrase

var defaultPassphraseDigestType DigestType = digestTypes["SHA_512"]

type KeyLabelPassphrase struct {
	digesttype DigestType
}

func NewKeyLabelPassphrase() KeyLabelPassphrase {
	return KeyLabelPassphrase{digesttype: defaultPassphraseDigestType}
}

func (l *KeyLabelPassphrase) name() string {
	return "PASSPHRASE"
}

func (l KeyLabelPassphrase) MarshalText() ([]byte, error) {
	text, err := l.digesttype.MarshalText()
	if err != nil {
		return nil, fmt.Errorf("failed marsheling key label passphrase: %v", err)
	}
	return text, nil
}

func (l *KeyLabelPassphrase) UnmarshalText(text []byte) error {
	dt, ok := digestTypes[string(text)]
	if !ok {
		return fmt.Errorf("unknown passphrase digest type: %s", text)
	}
	l.digesttype = dt
	return nil
}

func (l *KeyLabelPassphrase) Digest(passphrase []byte) ([]byte, error) {
	r, err := l.digesttype.digest(passphrase)
	if err != nil {
		return nil, fmt.Errorf("failed digesting passphrase: %v", err)
	}
	return r, nil
}

// Biometric

type KeyLabelBiometric struct {
	createdAt string
}

func (l *KeyLabelBiometric) name() string {
	return "BIOMETRIC"
}

func (l KeyLabelBiometric) MarshalText() ([]byte, error) {
	return []byte(l.createdAt), nil
}

func (l *KeyLabelBiometric) UnmarshalText(text []byte) error {
	l.createdAt = string(text)
	return nil
}
