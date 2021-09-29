// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/util"
)

func TestMan(t *testing.T) {
	tmpDir := t.TempDir()
	out, _ := execute(command{args: []string{"man", tmpDir}}, t, nil)
	assert.Equal(t, fmt.Sprintf("Success: Man pages written to %s\n", tmpDir), out)
	assert.True(t, util.PathExists(filepath.Join(tmpDir, "vespa.1")))
}
