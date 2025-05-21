// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ioutil

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestPathExists(t *testing.T) {
	assert.Equal(t, true, Exists("ioutil.go"))
	assert.Equal(t, false, Exists("nosuchthing.go"))

	tmpDir := t.TempDir()
	err := os.MkdirAll(tmpDir+"/no", 0755)
	assert.Nil(t, err)
	err = os.MkdirAll(tmpDir+"/no/such", 0)
	assert.Nil(t, err)
	assert.Equal(t, false, Exists(tmpDir+"/no/such/thing.go"))
}

func TestIsDir(t *testing.T) {
	tmpDir := t.TempDir()
	err := os.MkdirAll(tmpDir+"/no", 0755)
	assert.Nil(t, err)
	assert.Equal(t, true, IsDir(tmpDir+"/no"))
	err = os.MkdirAll(tmpDir+"/no/such", 0)
	assert.Nil(t, err)
	assert.Equal(t, true, IsDir(tmpDir+"/no/such"))
	assert.Equal(t, false, IsDir(tmpDir+"/no/such/thing.go"))
}

func TestIsRegularFile(t *testing.T) {
	assert.Equal(t, true, IsFile("ioutil.go"))
	assert.Equal(t, false, IsFile("."))
	tmpDir := t.TempDir()
	err := os.MkdirAll(tmpDir+"/no", 0755)
	assert.Nil(t, err)
	err = os.MkdirAll(tmpDir+"/no/such", 0)
	assert.Nil(t, err)
	assert.Equal(t, false, IsFile(tmpDir+"/no/such/thing.go"))
}

func TestIsExecutableFile(t *testing.T) {
	assert.Equal(t, false, IsExecutable("io.go"))
	assert.Equal(t, false, IsExecutable("nosuchthing.go"))
	tmpDir := t.TempDir()
	err := os.WriteFile(tmpDir+"/run.sh", []byte("#!/bin/sh\necho foo\n"), 0755)
	assert.Nil(t, err)
	assert.Equal(t, true, IsExecutable(tmpDir+"/run.sh"))
	/* unix only:
	out, err := BackTicksWithStderr.Run(tmpDir + "/run.sh")
	assert.Nil(t, err)
	assert.Equal(t, "foo\n", out)
	*/
}
