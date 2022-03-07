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
	cli, stdout, _ := newTestCLI(t)
	assert.Nil(t, cli.Run("man", tmpDir))
	assert.Equal(t, fmt.Sprintf("Success: Man pages written to %s\n", tmpDir), stdout.String())
	assert.True(t, util.PathExists(filepath.Join(tmpDir, "vespa.1")))
}
