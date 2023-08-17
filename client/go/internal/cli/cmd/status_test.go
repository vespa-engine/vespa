// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// status command tests
// Author: bratseth

package cmd

import (
	"io"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestStatusDeployCommand(t *testing.T) {
	assertDeployStatus("http://127.0.0.1:19071", []string{}, t)
}

func TestStatusDeployCommandWithURLTarget(t *testing.T) {
	assertDeployStatus("http://mydeploytarget:19071", []string{"-t", "http://mydeploytarget:19071"}, t)
}

func TestStatusDeployCommandWithLocalTarget(t *testing.T) {
	assertDeployStatus("http://127.0.0.1:19071", []string{"-t", "local"}, t)
}

func TestStatusCommand(t *testing.T) {
	assertStatus("http://127.0.0.1:8080", []string{}, t)
}

func TestStatusCommandMultiCluster(t *testing.T) {
	client := &mock.HTTPClient{}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	mockServiceStatus(client)
	assert.NotNil(t, cli.Run("status"))
	assert.Equal(t, "Error: no services exist\nHint: Deployment may not be ready yet\nHint: Try 'vespa status deployment'\n", stderr.String())

	mockServiceStatus(client, "foo", "bar")
	assert.Nil(t, cli.Run("status"))
	assert.Equal(t, `Container bar at http://127.0.0.1:8080 is ready
Container foo at http://127.0.0.1:8080 is ready
`, stdout.String())

	stdout.Reset()
	mockServiceStatus(client, "foo", "bar")
	assert.Nil(t, cli.Run("status", "--cluster", "foo"))
	assert.Equal(t, "Container foo at http://127.0.0.1:8080 is ready\n", stdout.String())
}

func TestStatusCommandWithUrlTarget(t *testing.T) {
	assertStatus("http://mycontainertarget:8080", []string{"-t", "http://mycontainertarget:8080"}, t)
}

func TestStatusCommandWithLocalTarget(t *testing.T) {
	assertStatus("http://127.0.0.1:8080", []string{"-t", "local"}, t)
}

func TestStatusError(t *testing.T) {
	client := &mock.HTTPClient{}
	mockServiceStatus(client, "default")
	client.NextStatus(500)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("status", "container"))
	assert.Equal(t,
		"Error: unhealthy container default: status 500 at http://127.0.0.1:8080/ApplicationStatus: wait timed out\n",
		stderr.String())

	stderr.Reset()
	client.NextResponseError(io.EOF)
	assert.NotNil(t, cli.Run("status", "container", "-t", "http://example.com"))
	assert.Equal(t,
		"Error: unhealthy container at http://example.com/ApplicationStatus: EOF\n",
		stderr.String())
}

func isLocalTarget(args []string) bool {
	for i := 0; i < len(args)-1; i++ {
		if args[i] == "-t" {
			return args[i+1] == "local"
		}
	}
	return true // local is default
}

func assertDeployStatus(expectedTarget string, args []string, t *testing.T) {
	t.Helper()
	client := &mock.HTTPClient{}
	client.NextResponse(mock.HTTPResponse{
		URI:    "/status.html",
		Status: 200,
	})
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	statusArgs := []string{"status", "deploy"}
	assert.Nil(t, cli.Run(append(statusArgs, args...)...))
	assert.Equal(t,
		"Deploy API at "+expectedTarget+" is ready\n",
		stdout.String())
	assert.Equal(t, expectedTarget+"/status.html", client.LastRequest.URL.String())
}

func assertStatus(expectedTarget string, args []string, t *testing.T) {
	t.Helper()
	client := &mock.HTTPClient{}
	clusterName := ""
	for i := 0; i < 2; i++ {
		if isLocalTarget(args) {
			clusterName = "foo"
			mockServiceStatus(client, clusterName)
		}
		client.NextResponse(mock.HTTPResponse{URI: "/ApplicationStatus", Status: 200})
	}
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	statusArgs := []string{"status"}
	assert.Nil(t, cli.Run(append(statusArgs, args...)...))
	prefix := "Container"
	if clusterName != "" {
		prefix += " " + clusterName
	}
	assert.Equal(t, prefix+" at "+expectedTarget+" is ready\n", stdout.String())
	assert.Equal(t, expectedTarget+"/ApplicationStatus", client.LastRequest.URL.String())

	// Test legacy command
	statusArgs = []string{"status query"}
	stdout.Reset()
	assert.Nil(t, cli.Run(append(statusArgs, args...)...))
	assert.Equal(t, prefix+" at "+expectedTarget+" is ready\n", stdout.String())
	assert.Equal(t, expectedTarget+"/ApplicationStatus", client.LastRequest.URL.String())
}

func assertDocumentStatus(target string, args []string, t *testing.T) {
	t.Helper()
	client := &mock.HTTPClient{}
	if isLocalTarget(args) {
		mockServiceStatus(client, "default")
	}
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	assert.Nil(t, cli.Run("status", "document"))
	assert.Equal(t,
		"Container (document API) at "+target+" is ready\n",
		stdout.String(),
		"vespa status container")
	assert.Equal(t, target+"/ApplicationStatus", client.LastRequest.URL.String())
}
