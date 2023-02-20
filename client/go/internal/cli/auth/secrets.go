// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"github.com/zalando/go-keyring"
)

type Keyring struct{}

// Set sets the given key/value pair with the given namespace.
func (k *Keyring) Set(namespace, key, value string) error {
	return keyring.Set(namespace, key, value)
}

// Get gets a value for the given namespace and key.
func (k *Keyring) Get(namespace, key string) (string, error) {
	return keyring.Get(namespace, key)
}

// Delete deletes a value for the given namespace and key.
func (k *Keyring) Delete(namespace, key string) error {
	return keyring.Delete(namespace, key)
}
