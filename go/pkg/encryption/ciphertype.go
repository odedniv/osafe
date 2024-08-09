package encryption

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"fmt"
)

var cipherTypes = map[string]CipherType{
	"AES_128": {cipherTypeImpl{
		name:        "AES_128",
		keySize:     16,
		block:       aes.NewCipher,
		encryptMode: cipher.NewCBCEncrypter,
		decryptMode: cipher.NewCBCDecrypter,
		pad:         pkcsPad,
		unpad:       pkcsUnpad,
	}},
}

type CipherType struct {
	impl cipherTypeImpl
}

func (ct CipherType) MarshalText() ([]byte, error) {
	return []byte(ct.impl.name), nil
}

func (ct *CipherType) UnmarshalText(text []byte) error {
	ct.impl = cipherTypes[string(text)].impl
	return nil
}

func (ct *CipherType) encrypt(key []byte, iv []byte, decrypted []byte) ([]byte, error) {
	// Block
	block, err := ct.impl.block(key[:ct.impl.keySize])
	if err != nil {
		return nil, fmt.Errorf("failed creating encrypt block: %v", err)
	}
	// Pad
	padded := ct.impl.pad(decrypted, block.BlockSize())
	// Encrypt
	encrypted := make([]byte, len(padded))
	ct.impl.encryptMode(block, iv).CryptBlocks(encrypted, padded)

	return encrypted, nil
}

func (ct *CipherType) decrypt(key []byte, iv []byte, encrypted []byte) ([]byte, error) {
	// Block
	block, err := ct.impl.block(key)
	if err != nil {
		return nil, fmt.Errorf("failed creating decrypt block: %v", err)
	}
	// Decrypt
	padded := make([]byte, len(encrypted))
	ct.impl.decryptMode(block, iv).CryptBlocks(padded, encrypted)
	// Unpad
	decrypted := ct.impl.unpad(padded)

	return decrypted, nil
}

func (ct *CipherType) ivSize() int {
	return ct.impl.keySize
}

type cipherTypeImpl struct {
	name        string
	keySize     int
	block       func(key []byte) (cipher.Block, error)
	encryptMode func(block cipher.Block, iv []byte) cipher.BlockMode
	decryptMode func(block cipher.Block, iv []byte) cipher.BlockMode
	pad         func(data []byte, blockSize int) []byte
	unpad       func(data []byte) []byte
}

// Utilities

func pkcsPad(data []byte, blockSize int) []byte {
	padSize := blockSize - len(data)%blockSize
	pad := bytes.Repeat([]byte{byte(padSize)}, padSize)
	return append(data, pad...)
}

func pkcsUnpad(data []byte) []byte {
	padSize := int(data[len(data)-1])
	if len(data) < padSize {
		return data
	}
	return data[:(len(data) - padSize)]
}
