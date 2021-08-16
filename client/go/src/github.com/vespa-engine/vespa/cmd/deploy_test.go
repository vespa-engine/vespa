// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// deploy command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestDeployZip(t *testing.T) {
    reset()
	assert.Equal(t,
	             "\x1b[32mSuccess \n",
	             executeCommand(t, []string{"deploy", "testdata/application.zip"}, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", t)
}

func TestDeployZipWithTargetArgument(t *testing.T) {
    reset()
	assert.Equal(t,
	             "\x1b[32mSuccess \n",
	             executeCommand(t, []string{"deploy", "testdata/application.zip", "-t", "http://target:19071"}, []string{}))
	assertDeployRequestMade("http://target:19071", t)

	assert.Equal(t,
	             "\x1b[32mSuccess \n",
	             executeCommand(t, []string{"deploy", "testdata/application.zip", "--target", "http://target2:19071"}, []string{}))
	assertDeployRequestMade("http://target2:19071", t)
}

func TestDeployDirectory(t *testing.T) {
    reset()
	assert.Equal(t,
	             "\x1b[32mSuccess \n",
	             executeCommand(t, []string{"deploy", "testdata/src/main/application"}, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", t)
}

func assertDeployRequestMade(target string, t *testing.T) {
    assert.Equal(t, target + "/application/v2/tenant/default/prepareandactivate", lastRequest.URL.String())
    assert.Equal(t, "application/zip", lastRequest.Header.Get("Content-Type"))
    assert.Equal(t, "POST", lastRequest.Method)
    var body = lastRequest.Body
    assert.NotNil(t, body)
    buf := make([]byte, 7) // Just check the first few bytes
    body.Read(buf)
    assert.Equal(t, "PK\x03\x04\x14\x00\b", string(buf))
}