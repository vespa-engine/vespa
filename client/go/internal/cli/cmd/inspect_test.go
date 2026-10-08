// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package cmd

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func writeProfileResult(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "profile.json")
	require.NoError(t, os.WriteFile(path, []byte(`{
  "timing": {
    "querytime": 0.5,
    "summaryfetchtime": 0.25,
    "searchtime": 1.0
  }
}`), 0o600))
	return path
}

func TestInspectProfileJSONFormat(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)

	assert.NoError(t, cli.Run("inspect", "profile", "--profile-file", writeProfileResult(t), "--format", "json"))
	assert.Equal(t, "", stderr.String())

	var output map[string]any
	require.NoError(t, json.Unmarshal(stdout.Bytes(), &output))
	assert.Equal(t, float64(1), output["schemaVersion"])
	timing := output["timing"].(map[string]any)
	assert.Equal(t, float64(1000), timing["totalMs"])
	assert.Equal(t, float64(500), timing["queryMs"])
	assert.Equal(t, float64(250), timing["summaryMs"])
	assert.Equal(t, float64(250), timing["otherMs"])
}

func TestInspectProfileJSONOutputFile(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	outputPath := filepath.Join(t.TempDir(), "summary.json")

	assert.NoError(t, cli.Run("inspect", "profile", "--profile-file", writeProfileResult(t), "--format", "json", "--output", outputPath))
	assert.Equal(t, "", stderr.String())
	assert.Equal(t, "", stdout.String())

	content, err := os.ReadFile(outputPath)
	require.NoError(t, err)
	var output map[string]any
	require.NoError(t, json.Unmarshal(content, &output))
	assert.Equal(t, float64(1), output["schemaVersion"])
}

func TestInspectProfileInvalidFormatDoesNotTruncateOutputFile(t *testing.T) {
	cli, _, _ := newTestCLI(t)
	outputPath := filepath.Join(t.TempDir(), "summary.json")
	require.NoError(t, os.WriteFile(outputPath, []byte("keep me"), 0o600))

	assert.Error(t, cli.Run("inspect", "profile", "--profile-file", writeProfileResult(t), "--format", "jsn", "--output", outputPath))

	content, err := os.ReadFile(outputPath)
	require.NoError(t, err)
	assert.Equal(t, "keep me", string(content))
}
