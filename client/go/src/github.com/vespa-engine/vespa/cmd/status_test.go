// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// status command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestStatusConfigServerCommand(t *testing.T) {
	assert.Equal(t,
	             "\x1b[32mConfig server at http://127.0.0.1:19071 is ready \n",
	             executeCommand(t, []string{"status", "config-server"}),
	             "vespa status config-server")
    assert.Equal(t, "http://127.0.0.1:19071/ApplicationStatus", lastRequest.URL.String())
}

func TestStatusContainerCommand(t *testing.T) {
	assert.Equal(t,
	             "\x1b[32mContainer at http://127.0.0.1:8080 is ready \n",
	             executeCommand(t, []string{"status", "container"}),
	             "vespa status container")
    assert.Equal(t, "http://127.0.0.1:8080/ApplicationStatus", lastRequest.URL.String())

	assert.Equal(t,
	             "\x1b[32mContainer at http://127.0.0.1:8080 is ready \n",
	             executeCommand(t, []string{"status"}),
	             "vespa status (the default)")
    assert.Equal(t, "http://127.0.0.1:8080/ApplicationStatus", lastRequest.URL.String())
}

func TestStatusErrorResponse(t *testing.T) {
    nextStatus = 500
	assert.Equal(t,
	             "\x1b[31mContainer at http://127.0.0.1:8080 is not ready \n\x1b[33mResponse status:  \n",
	             executeCommand(t, []string{"status", "container"}),
	             "vespa status container")
}
