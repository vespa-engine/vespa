// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"path/filepath"
	"testing"

	"github.com/vespa-engine/vespa/client/go/mock"
)

func newTestCLI(t *testing.T, envVars ...string) (*CLI, *bytes.Buffer, *bytes.Buffer) {
	t.Helper()
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	cacheDir := filepath.Join(t.TempDir(), ".cache", "vespa")
	env := []string{"VESPA_CLI_HOME=" + homeDir, "VESPA_CLI_CACHE_DIR=" + cacheDir}
	env = append(env, envVars...)
	var (
		stdout bytes.Buffer
		stderr bytes.Buffer
	)
	cli, err := New(&stdout, &stderr, env)
	if err != nil {
		t.Fatal(err)
	}
	cli.httpClient = &mock.HTTPClient{}
	cli.exec = &mock.Exec{}
	return cli, &stdout, &stderr
}
