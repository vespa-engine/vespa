// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"os"

	"github.com/zalando/go-keyring"
)

type realKeyring struct{}
type dummyKeyring struct{}

func NewKeyring() SecretStore {
	if env := os.Getenv("VESPA_CLI_DUMMY_KEYRING"); env != "" {
		return &dummyKeyring{}
	}
	return &realKeyring{}
}

// Set sets the given key/value pair with the given namespace.
func (k *realKeyring) Set(namespace, key, value string) error {
	return keyring.Set(namespace, key, value)
}

// Get gets a value for the given namespace and key.
func (k *realKeyring) Get(namespace, key string) (string, error) {
	return keyring.Get(namespace, key)
}

// Delete deletes a value for the given namespace and key.
func (k *realKeyring) Delete(namespace, key string) error {
	return keyring.Delete(namespace, key)
}

func dummyRingFileName(namespace, key string) (string, error) {
	userHome, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	home := userHome + "/.vespa"
	if err := os.MkdirAll(home, 0700); err != nil {
		return "", err
	}
	return home + "/keyring." + namespace + "." + key, nil
}

// Set sets the given key/value pair with the given namespace.
func (k *dummyKeyring) Set(namespace, key, value string) error {
	fn, err := dummyRingFileName(namespace, key)
	if err != nil {
		return err
	}
	return os.WriteFile(fn, []byte(value), 0400)
}

// Get gets a value for the given namespace and key.
func (k *dummyKeyring) Get(namespace, key string) (string, error) {
	fn, err := dummyRingFileName(namespace, key)
	if err != nil {
		return "", err
	}
	a, b := os.ReadFile(fn)
	return string(a), b
}

// Delete deletes a value for the given namespace and key.
func (k *dummyKeyring) Delete(namespace, key string) error {
	fn, err := dummyRingFileName(namespace, key)
	if err != nil {
		return err
	}
	return os.Remove(fn)
}
