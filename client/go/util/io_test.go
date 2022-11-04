// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestPathExists(t *testing.T) {
	assert.Equal(t, true, PathExists("io.go"))
	assert.Equal(t, false, PathExists("nosuchthing.go"))

	tmpDir := t.TempDir()
	err := os.MkdirAll(tmpDir+"/no", 0755)
	assert.Nil(t, err)
	err = os.MkdirAll(tmpDir+"/no/such", 0)
	assert.Nil(t, err)
	assert.Equal(t, false, PathExists(tmpDir+"/no/such/thing.go"))
}

func TestIsDir(t *testing.T) {
	tmpDir := t.TempDir()
	err := os.MkdirAll(tmpDir+"/no", 0755)
	assert.Nil(t, err)
	assert.Equal(t, true, IsDirectory(tmpDir+"/no"))
	err = os.MkdirAll(tmpDir+"/no/such", 0)
	assert.Nil(t, err)
	assert.Equal(t, true, IsDirectory(tmpDir+"/no/such"))
	assert.Equal(t, false, IsDirectory(tmpDir+"/no/such/thing.go"))
}

func TestIsRegularFile(t *testing.T) {
	assert.Equal(t, true, IsRegularFile("io.go"))
	assert.Equal(t, false, IsRegularFile("."))
	tmpDir := t.TempDir()
	err := os.MkdirAll(tmpDir+"/no", 0755)
	assert.Nil(t, err)
	err = os.MkdirAll(tmpDir+"/no/such", 0)
	assert.Nil(t, err)
	assert.Equal(t, false, IsRegularFile(tmpDir+"/no/such/thing.go"))
}

func TestIsExecutableFile(t *testing.T) {
	assert.Equal(t, false, IsExecutableFile("io.go"))
	assert.Equal(t, false, IsExecutableFile("nosuchthing.go"))
	tmpDir := t.TempDir()
	err := os.WriteFile(tmpDir+"/run.sh", []byte("#!/bin/sh\necho foo\n"), 0755)
	assert.Nil(t, err)
	assert.Equal(t, true, IsExecutableFile(tmpDir+"/run.sh"))
	/* unix only:
	out, err := BackTicksWithStderr.Run(tmpDir + "/run.sh")
	assert.Nil(t, err)
	assert.Equal(t, "foo\n", out)
	*/
}
