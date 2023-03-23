// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"crypto/tls"
	"path/filepath"
	"testing"

	"github.com/vespa-engine/vespa/client/go/internal/cli/auth/auth0"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/util"
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
	cli.auth0Factory = func(httpClient util.HTTPClient, options auth0.Options) (auth0Client, error) {
		return &mockAuth0{}, nil
	}
	cli.ztsFactory = func(httpClient util.HTTPClient, url string) (ztsClient, error) {
		return &mockZTS{}, nil
	}
	return cli, &stdout, &stderr
}

type mockZTS struct{}

func (z *mockZTS) AccessToken(domain string, cert tls.Certificate) (string, error) { return "", nil }

type mockAuth0 struct{ hasCredentials bool }

func (a *mockAuth0) AccessToken() (string, error) { return "", nil }

func (a *mockAuth0) HasCredentials() bool { return a.hasCredentials }
