// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAPIKey(t *testing.T) {
	homeDir := t.TempDir()
	keyFile := homeDir + "/.vespa/t1.api-key.pem"

	out := execute(command{args: []string{"api-key", "-a", "t1.a1.i1"}, homeDir: homeDir}, t, nil)
	assert.True(t, strings.HasPrefix(out, "Success: API private key written to "+keyFile+"\n"))

	out = execute(command{args: []string{"api-key", "-a", "t1.a1.i1"}, homeDir: homeDir}, t, nil)
	assert.True(t, strings.HasPrefix(out, "Error: File "+keyFile+" already exists\nHint: Use -f to overwrite it\n"))
	assert.True(t, strings.Contains(out, "This is your public key"))
}
