// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAPIKey(t *testing.T) {
	configDir := t.TempDir()
	keyFile := configDir + "/.vespa/t1.api-key.pem"

	out := execute(command{args: []string{"api-key", "-a", "t1.a1.i1"}, configDir: configDir}, t, nil)
	assert.Equal(t, "Success: API key written to "+keyFile+"\n", out)

	out = execute(command{args: []string{"api-key", "-a", "t1.a1.i1"}, configDir: configDir}, t, nil)
	assert.Equal(t, "Error: File "+keyFile+" already exists\nHint: Use -f to overwrite it\n", out)
}
