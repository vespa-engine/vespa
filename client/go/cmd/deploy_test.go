// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// deploy command tests
// Author: bratseth

package cmd

import (
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDeployZip(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mSuccess\x1b[0m\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget/target/application.zip"}, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", client, t)
}

func TestDeployZipWithURLTargetArgument(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mSuccess\x1b[0m\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget/target/application.zip", "-t", "http://target:19071"}, []string{}))
	assertDeployRequestMade("http://target:19071", client, t)
}

func TestDeployZipWitLocalTargetArgument(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mSuccess\x1b[0m\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget/target/application.zip", "-t", "local"}, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", client, t)
}

func TestDeploySourceDirectory(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mSuccess\x1b[0m\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withSource/src/main/application"}, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", client, t)
}

func TestDeployApplicationDirectoryWithSource(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mSuccess\x1b[0m\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withSource"}, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", client, t)
}

func TestDeployApplicationDirectoryWithTarget(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mSuccess\x1b[0m\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget"}, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", client, t)
}

func TestDeployApplicationDirectoryWithEmptyTarget(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[31mpom.xml exists but no target/application.zip. Run mvn package first\x1b[0m\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withEmptyTarget"}, []string{}))
}

func TestDeployApplicationPackageError(t *testing.T) {
	assertApplicationPackageError(t, 401, "Application package error")
}

func TestDeployError(t *testing.T) {
	assertDeployServerError(t, 501, "Deploy service error")
}

// TODO: Test prepare and activate prepared

func assertDeployRequestMade(target string, client *mockHttpClient, t *testing.T) {
	assert.Equal(t, target+"/application/v2/tenant/default/prepareandactivate", client.lastRequest.URL.String())
	assert.Equal(t, "application/zip", client.lastRequest.Header.Get("Content-Type"))
	assert.Equal(t, "POST", client.lastRequest.Method)
	var body = client.lastRequest.Body
	assert.NotNil(t, body)
	buf := make([]byte, 7) // Just check the first few bytes
	body.Read(buf)
	assert.Equal(t, "PK\x03\x04\x14\x00\b", string(buf))
}

func assertApplicationPackageError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{nextStatus: status, nextBody: errorMessage}
	assert.Equal(t,
		"\x1b[31mInvalid application package (Status "+strconv.Itoa(status)+"):\x1b[0m\n"+errorMessage+"\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget/target/application.zip"}, []string{}))
}

func assertDeployServerError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{nextStatus: status, nextBody: errorMessage}
	assert.Equal(t,
		"\x1b[31mError from deploy service at 127.0.0.1:19071 (Status "+strconv.Itoa(status)+"):\x1b[0m\n"+errorMessage+"\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget/target/application.zip"}, []string{}))
}
