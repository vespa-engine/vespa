// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAPIKey(t *testing.T) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	keyFile := filepath.Join(homeDir, "t1.api-key.pem")

	out, _ := execute(command{args: []string{"api-key", "-a", "t1.a1.i1"}, homeDir: homeDir}, t, nil)
	assert.Contains(t, out, "Success: API private key written to "+keyFile+"\n")

	out, outErr := execute(command{args: []string{"api-key", "-a", "t1.a1.i1"}, homeDir: homeDir}, t, nil)
	assert.Contains(t, outErr, "Error: File "+keyFile+" already exists\nHint: Use -f to overwrite it\n")
	assert.Contains(t, out, "This is your public key")
}
