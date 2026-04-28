// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDummyKeyringSaveOverwrite(t *testing.T) {
	t.Setenv("HOME", t.TempDir())
	k := NewKeyringWithOptions(true)

	err := k.Set("test", "user", "password1")
	assert.Nil(t, err)
	err = k.Set("test", "user", "password2")
	assert.Nil(t, err)
}
