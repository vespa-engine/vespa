// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package config

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestConfig(t *testing.T) {
	config := New()
	config.Set("key1", "value1")
	config.Set("key2", "value2")
	config.Set("key3", "value3")
	assert.Equal(t, []string{"key1", "key2", "key3"}, config.Keys())

	v, ok := config.Get("key3")
	assert.True(t, ok)
	assert.Equal(t, "value3", v)

	config.Del("key3")
	_, ok = config.Get("key3")
	assert.False(t, ok)

	var buf bytes.Buffer
	require.Nil(t, config.Write(&buf))
	assert.Equal(t, "key1: value1\nkey2: value2\n", buf.String())

	unmarshalled, err := Read(&buf)
	require.Nil(t, err)
	assert.Equal(t, config, unmarshalled)

	filename := filepath.Join(t.TempDir(), "config.yaml")
	require.Nil(t, config.WriteFile(filename))
	data, err := os.ReadFile(filename)
	require.Nil(t, err)
	assert.Equal(t, "key1: value1\nkey2: value2\n", string(data))
}
