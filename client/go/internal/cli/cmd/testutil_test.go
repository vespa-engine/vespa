// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/cli/auth/auth0"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newTestCLI(t *testing.T, envVars ...string) (*CLI, *bytes.Buffer, *bytes.Buffer) {
	t.Helper()
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	cacheDir := filepath.Join(t.TempDir(), ".cache", "vespa")
	// Pre-set default_config_scope to suppress the "unset" warning in all tests
	// that are not specifically testing that warning.
	require.Nil(t, os.MkdirAll(homeDir, 0o700))
	require.Nil(t, os.WriteFile(filepath.Join(homeDir, "config.yaml"), []byte("default_config_scope: global\n"), 0o600))
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
	cli.httpClientFactory = func(timeout time.Duration) httputil.Client { return httpClient }
	cli.httpClient = httpClient
	cli.exec = &mock.Exec{}
	cli.auth0Factory = func(httpClient httputil.Client, options auth0.Options) (vespa.Authenticator, error) {
		return &mockAuthenticator{}, nil
	}
	cli.ztsFactory = func(httpClient httputil.Client, domain, url string) (vespa.Authenticator, error) {
		return &mockAuthenticator{}, nil
	}
	cli.retryInterval = time.Hour          // Disable waiting in tests. Waiting is short-circuited if --wait < retryInterval
	cli.sleeper = func(d time.Duration) {} // No-op sleeping
	return cli, &stdout, &stderr
}

func mockServiceStatus(client *mock.HTTPClient, clusterNames ...string) {
	var serviceObjects []string
	for _, name := range clusterNames {
		service := fmt.Sprintf(`{
      "clusterName": "%s",
      "host": "localhost",
      "port": 8080,
      "type": "container",
      "url": "http://localhost:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge/localhost:8080",
      "currentGeneration": 1
    }
`, name)
		serviceObjects = append(serviceObjects, service)
	}
	services := "[]"
	if len(serviceObjects) > 0 {
		services = "[" + strings.Join(serviceObjects, ",") + "]"
	}
	response := fmt.Sprintf(`
{
  "services": %s,
  "currentGeneration": 1
}`, services)
	client.NextResponse(mock.HTTPResponse{
		URI:    "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge",
		Status: 200,
		Body:   []byte(response),
	})
}

type mockAuthenticator struct{}

func (a *mockAuthenticator) Authenticate(request *http.Request) error { return nil }
