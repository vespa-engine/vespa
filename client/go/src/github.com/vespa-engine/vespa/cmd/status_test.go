// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// status command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestStatusConfigServerCommand(t *testing.T) {
    reset()
    assertConfigServerStatus("http://127.0.0.1:19071", []string{}, t)
}

func TestStatusConfigServerCommandWithURLTarget(t *testing.T) {
    reset()
    assertConfigServerStatus("http://mydeploytarget", []string{"-t", "http://mydeploytarget"}, t)
}

func TestStatusConfigServerCommandWithLocalTarget(t *testing.T) {
    reset()
    assertConfigServerStatus("http://127.0.0.1:19071", []string{"-t", "local"}, t)
}

func TestStatusContainerCommand(t *testing.T) {
    reset()
    assertContainerStatus("http://127.0.0.1:8080", []string{}, t)
}

func TestStatusContainerCommandWithUrlTarget(t *testing.T) {
    reset()
    assertContainerStatus("http://mycontainertarget", []string{"-t", "http://mycontainertarget"}, t)
}

func TestStatusContainerCommandWithLocalTarget(t *testing.T) {
    reset()
    assertContainerStatus("http://127.0.0.1:8080", []string{"-t", "local"}, t)
}

func TestStatusErrorResponse(t *testing.T) {
    reset()
    assertContainerError("http://127.0.0.1:8080", []string{}, t)
}

func assertConfigServerStatus(target string, args []string, t *testing.T) {
	assert.Equal(t,
	             "\x1b[32mConfig server at " + target + " is ready\n",
	             executeCommand(t, []string{"status", "config-server"}, args),
	             "vespa status config-server")
    assert.Equal(t, target + "/ApplicationStatus", lastRequest.URL.String())
}

func assertContainerStatus(target string, args []string, t *testing.T) {
	assert.Equal(t,
	             "\x1b[32mContainer at " + target + " is ready\n",
	             executeCommand(t, []string{"status", "container"}, args),
	             "vespa status container")
    assert.Equal(t, target + "/ApplicationStatus", lastRequest.URL.String())

	assert.Equal(t,
	             "\x1b[32mContainer at " + target + " is ready\n",
	             executeCommand(t, []string{"status"}, args),
	             "vespa status (the default)")
    assert.Equal(t, target + "/ApplicationStatus", lastRequest.URL.String())
}

func assertContainerError(target string, args []string, t *testing.T) {
    nextStatus = 500
	assert.Equal(t,
	             "\x1b[31mContainer at " + target + " is not ready\n\x1b[33mResponse status: \n",
	             executeCommand(t, []string{"status", "container"}, args),
	             "vespa status container")
}