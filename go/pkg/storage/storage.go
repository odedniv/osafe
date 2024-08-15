package storage

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"slices"
	"time"

	"github.com/odedniv/osafe/go/pkg/encryption"
)

var storageFilename = "osafe.json"
var storages = []storage{&driveStorage{}}

func Read() (*encryption.Message, error) {
	// Reading
	cs, errs := readAll()
	err := errors.Join(errs...)
	if err != nil {
		return nil, fmt.Errorf("failed reading from storages: %v", err)
	} else if len(cs) == 0 {
		return nil, nil
	}
	// Finding newest
	newest := slices.MaxFunc(cs, func(a, b contentStorage) int {
		return time.Time.Compare(a.content.modifiedTime, b.content.modifiedTime)
	})
	// Writing to storages older than newest
	err = errors.Join(writeOlder(newest, cs)...)
	if err != nil {
		return nil, fmt.Errorf("failed writing to old storages: %v", err)
	}
	// Decoding
	var m encryption.Message
	err = json.Unmarshal(newest.content.bytes, &m)
	if err != nil {
		return nil, fmt.Errorf("failed unmarshaling message from storage: %v", err)
	}
	return &m, nil
}

func Write(m encryption.Message) error {
	// Encoding
	bytes, err := json.Marshal(m)
	if err != nil {
		return fmt.Errorf("failed marshaling message to storage: %v", err)
	}
	c := content{bytes, time.Now()}
	// Writing
	err = errors.Join(writeAll(c)...)
	if err != nil {
		return fmt.Errorf("failed writing to storages: %v", err)
	}
	return nil
}

func readAll() ([]contentStorage, []error) {
	type info struct {
		s   storage
		c   content
		err error
	}

	var chs [](chan info)
	for _, s := range storages {
		ch := make(chan info)
		chs = append(chs, ch)
		go func(s storage) {
			c, err := s.read()
			ch <- info{s, c, err}
		}(s)
	}

	var contents []contentStorage
	var errs []error
	for _, ch := range chs {
		info := <-ch
		errs = append(errs, info.err)
		contents = append(contents, contentStorage{info.s, info.c})
	}

	return contents, errs
}

func writeOlder(newest contentStorage, cs []contentStorage) []error {
	var chs [](chan error)
	for _, c := range cs {
		if c.storage == newest.storage || bytes.Equal(c.content.bytes, newest.content.bytes) {
			continue
		}
		ch := make(chan error)
		chs = append(chs, ch)
		go func(s storage) { ch <- s.write(newest.content) }(c.storage)
	}

	var errs []error
	for _, ch := range chs {
		errs = append(errs, <-ch)
	}
	return errs
}

func writeAll(c content) []error {
	var chs [](chan error)
	for _, s := range storages {
		ch := make(chan error)
		chs = append(chs, ch)
		go func(s storage) { ch <- s.write(c) }(s)
	}

	var errs []error
	for _, ch := range chs {
		errs = append(errs, <-ch)
	}
	return errs
}

type storage interface {
	read() (content, error)
	write(content content) error
}

type content struct {
	bytes        []byte
	modifiedTime time.Time
}

type contentStorage struct {
	storage storage
	content content
}
