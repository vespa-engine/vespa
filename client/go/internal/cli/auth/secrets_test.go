// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDummyKeyringOverwritesReadOnlyFile(t *testing.T) {
	// Simulate the bug: dummyKeyring.Set creates a 0o400 file, then a
	// second Set must succeed despite the file being read-only.
	dir := t.TempDir()
	origHome := os.Getenv("HOME")
	t.Setenv("HOME", dir)
	defer os.Setenv("HOME", origHome)

	k := &dummyKeyring{}
	require.NoError(t, k.Set("ns", "key", "first"))

	fn := filepath.Join(dir, ".vespa", "keyring.ns.key")
	info, err := os.Stat(fn)
	require.NoError(t, err)
	assert.Equal(t, os.FileMode(0o400), info.Mode().Perm())

	// Second write must not fail even though the file is read-only
	require.NoError(t, k.Set("ns", "key", "second"))

	val, err := k.Get("ns", "key")
	require.NoError(t, err)
	assert.Equal(t, "second", val)
}

func TestFallbackKeyringSet(t *testing.T) {
	k := &fallbackKeyring{
		primary:   &failingStore{},
		secondary: &dummyTestStore{data: map[string]string{}},
	}
	// Primary fails, so the value should end up in secondary
	require.NoError(t, k.Set("ns", "key", "value"))
	val, err := k.Get("ns", "key")
	require.NoError(t, err)
	assert.Equal(t, "value", val)
}

func TestFallbackKeyringGetPrimaryFirst(t *testing.T) {
	primary := &dummyTestStore{data: map[string]string{"ns/key": "from-primary"}}
	secondary := &dummyTestStore{data: map[string]string{"ns/key": "from-secondary"}}
	k := &fallbackKeyring{primary: primary, secondary: secondary}

	val, err := k.Get("ns", "key")
	require.NoError(t, err)
	assert.Equal(t, "from-primary", val)
}

func TestFallbackKeyringGetFallsBack(t *testing.T) {
	k := &fallbackKeyring{
		primary:   &failingStore{},
		secondary: &dummyTestStore{data: map[string]string{"ns/key": "from-file"}},
	}
	val, err := k.Get("ns", "key")
	require.NoError(t, err)
	assert.Equal(t, "from-file", val)
}

func TestFallbackKeyringDeleteBoth(t *testing.T) {
	primary := &dummyTestStore{data: map[string]string{"ns/key": "a"}}
	secondary := &dummyTestStore{data: map[string]string{"ns/key": "b"}}
	k := &fallbackKeyring{primary: primary, secondary: secondary}

	require.NoError(t, k.Delete("ns", "key"))
	_, ok := primary.data["ns/key"]
	assert.False(t, ok)
	_, ok = secondary.data["ns/key"]
	assert.False(t, ok)
}

// --- test helpers ---

type failingStore struct{}

func (f *failingStore) Set(_, _, _ string) error     { return os.ErrPermission }
func (f *failingStore) Get(_, _ string) (string, error) { return "", os.ErrNotExist }
func (f *failingStore) Delete(_, _ string) error      { return os.ErrNotExist }

type dummyTestStore struct {
	data map[string]string
}

func (d *dummyTestStore) Set(namespace, key, value string) error {
	d.data[namespace+"/"+key] = value
	return nil
}

func (d *dummyTestStore) Get(namespace, key string) (string, error) {
	v, ok := d.data[namespace+"/"+key]
	if !ok {
		return "", os.ErrNotExist
	}
	return v, nil
}

func (d *dummyTestStore) Delete(namespace, key string) error {
	delete(d.data, namespace+"/"+key)
	return nil
}
