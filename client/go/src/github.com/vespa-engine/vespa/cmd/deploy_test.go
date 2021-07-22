// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// deploy command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestDeployCommand(t *testing.T) {
	assert.Equal(t,
	             "\x1b[32mSuccess \n",
	             executeCommand(t, []string{"deploy", "testdata/application.zip"}))
    assert.Equal(t, "http://127.0.0.1:19071/application/v2/tenant/default/prepareandactivate", lastRequest.URL.String())
    assert.Equal(t, "application/zip", lastRequest.Header.Get("Content-Type"))
}
