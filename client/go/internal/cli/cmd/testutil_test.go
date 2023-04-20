// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"net/http"
	"path/filepath"
	"testing"

	"github.com/vespa-engine/vespa/client/go/internal/cli/auth/auth0"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
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
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	cli.exec = &mock.Exec{}
	cli.auth0Factory = func(httpClient util.HTTPClient, options auth0.Options) (vespa.Authenticator, error) {
		return &mockAuthenticator{}, nil
	}
	cli.ztsFactory = func(httpClient util.HTTPClient, domain, url string) (vespa.Authenticator, error) {
		return &mockAuthenticator{}, nil
	}
	return cli, &stdout, &stderr
}

type mockAuthenticator struct{}

func (a *mockAuthenticator) Authenticate(request *http.Request) error { return nil }
