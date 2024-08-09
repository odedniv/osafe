package encryption

import (
	"crypto"
	"fmt"
	"hash"
)

var digestTypes = map[string]DigestType{
	"SHA_1":   {digestTypeImpl{name: "SHA_1", hash: crypto.SHA1.New}},
	"SHA_512": {digestTypeImpl{name: "SHA_512", hash: crypto.SHA512.New}},
}

type DigestType struct {
	impl digestTypeImpl
}

func (dt DigestType) MarshalText() ([]byte, error) {
	return []byte(dt.impl.name), nil
}

func (dt *DigestType) UnmarshalText(text []byte) error {
	dt.impl = digestTypes[string(text)].impl
	return nil
}

func (dt *DigestType) digest(data []byte) ([]byte, error) {
	h := dt.impl.hash()
	_, err := h.Write(data)
	if err != nil {
		return nil, fmt.Errorf("failed writing to hash: %v", err)
	}
	return h.Sum(nil), nil
}

type digestTypeImpl struct {
	name string
	hash func() hash.Hash
}
