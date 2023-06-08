// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// status command tests
// Author: bratseth

package cmd

import (
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

func TestStatusQueryCommand(t *testing.T) {
	assertQueryStatus("http://127.0.0.1:8080", []string{}, t)
}

func TestStatusQueryCommandWithUrlTarget(t *testing.T) {
	assertQueryStatus("http://mycontainertarget:8080", []string{"-t", "http://mycontainertarget:8080"}, t)
}

func TestStatusQueryCommandWithLocalTarget(t *testing.T) {
	assertQueryStatus("http://127.0.0.1:8080", []string{"-t", "local"}, t)
}

func TestStatusDocumentCommandWithLocalTarget(t *testing.T) {
	assertDocumentStatus("http://127.0.0.1:8080", []string{"-t", "local"}, t)
}

func TestStatusErrorResponse(t *testing.T) {
	assertQueryStatusError("http://127.0.0.1:8080", []string{}, t)
}

func assertDeployStatus(target string, args []string, t *testing.T) {
	client := &mock.HTTPClient{}
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	statusArgs := []string{"status", "deploy"}
	assert.Nil(t, cli.Run(append(statusArgs, args...)...))
	assert.Equal(t,
		"Deploy API at "+target+" is ready\n",
		stdout.String(),
		"vespa status config-server")
	assert.Equal(t, target+"/status.html", client.LastRequest.URL.String())
}

func assertQueryStatus(target string, args []string, t *testing.T) {
	client := &mock.HTTPClient{}
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	statusArgs := []string{"status", "query"}
	assert.Nil(t, cli.Run(append(statusArgs, args...)...))
	assert.Equal(t,
		"Container (query API) at "+target+" is ready\n",
		stdout.String(),
		"vespa status container")
	assert.Equal(t, target+"/ApplicationStatus", client.LastRequest.URL.String())

	statusArgs = []string{"status"}
	stdout.Reset()
	assert.Nil(t, cli.Run(append(statusArgs, args...)...))
	assert.Equal(t,
		"Container (query API) at "+target+" is ready\n",
		stdout.String(),
		"vespa status (the default)")
	assert.Equal(t, target+"/ApplicationStatus", client.LastRequest.URL.String())
}

func assertDocumentStatus(target string, args []string, t *testing.T) {
	client := &mock.HTTPClient{}
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	assert.Nil(t, cli.Run("status", "document"))
	assert.Equal(t,
		"Container (document API) at "+target+" is ready\n",
		stdout.String(),
		"vespa status container")
	assert.Equal(t, target+"/ApplicationStatus", client.LastRequest.URL.String())
}

func assertQueryStatusError(target string, args []string, t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextStatus(500)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("status", "container"))
	assert.Equal(t,
		"Error: Container (query API) at "+target+" is not ready: status 500\n",
		stderr.String(),
		"vespa status container")
}
