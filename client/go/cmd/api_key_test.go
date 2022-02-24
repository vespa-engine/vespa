// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAPIKey(t *testing.T) {
	t.Run("auth api-key", func(t *testing.T) {
		testAPIKey(t, []string{"auth", "api-key"})
	})
	t.Run("api-key (deprecated)", func(t *testing.T) {
		testAPIKey(t, []string{"api-key"})
	})
}

func testAPIKey(t *testing.T, subcommand []string) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	keyFile := filepath.Join(homeDir, "t1.api-key.pem")

	args := append(subcommand, "-a", "t1.a1.i1")
	out, _ := execute(command{args: args, homeDir: homeDir}, t, nil)
	assert.Contains(t, out, "Success: API private key written to "+keyFile+"\n")

	out, outErr := execute(command{args: args, homeDir: homeDir}, t, nil)
	assert.Contains(t, outErr, "Error: refusing to overwrite "+keyFile+"\nHint: Use -f to overwrite it\n")
	assert.Contains(t, out, "This is your public key")
}
