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
	assertDeploy("testdata/applications/withTarget/target/application.zip",
		[]string{"deploy", "testdata/applications/withTarget/target/application.zip"}, t)
}

func TestDeployZipWithURLTargetArgument(t *testing.T) {
	applicationPackage := "testdata/applications/withTarget/target/application.zip"
	arguments := []string{"deploy", "testdata/applications/withTarget/target/application.zip", "-t", "http://target:19071"}

	client := &mockHttpClient{}
	assert.Equal(t,
		"Success: Deployed "+applicationPackage+"\n",
		executeCommand(t, client, arguments, []string{}))
	assertDeployRequestMade("http://target:19071", client, t)
}

func TestDeployZipWitLocalTargetArgument(t *testing.T) {
	assertDeploy("testdata/applications/withTarget/target/application.zip",
		[]string{"deploy", "testdata/applications/withTarget/target/application.zip", "-t", "local"}, t)
}

func TestDeploySourceDirectory(t *testing.T) {
	assertDeploy("testdata/applications/withSource/src/main/application",
		[]string{"deploy", "testdata/applications/withSource/src/main/application"}, t)
}

func TestDeployApplicationDirectoryWithSource(t *testing.T) {
	assertDeploy("testdata/applications/withSource/src/main/application",
		[]string{"deploy", "testdata/applications/withSource"}, t)
}

func TestDeployApplicationDirectoryWithPomAndTarget(t *testing.T) {
	assertDeploy("testdata/applications/withTarget/target/application.zip",
		[]string{"deploy", "testdata/applications/withTarget"}, t)
}

func TestDeployApplicationDirectoryWithPomAndEmptyTarget(t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"Error: pom.xml exists but no target/application.zip. Run mvn package first\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withEmptyTarget"}, []string{}))
}

func TestDeployApplicationPackageErrorWithUnexpectedNonJson(t *testing.T) {
	assertApplicationPackageError(t, 401,
		"Raw text error",
		"Raw text error")
}

func TestDeployApplicationPackageErrorWithUnexpectedJson(t *testing.T) {
	assertApplicationPackageError(t, 401,
		`{
    "some-unexpected-json": "Invalid XML, error in services.xml: element \"nosuch\" not allowed here"
}`,
		`{"some-unexpected-json": "Invalid XML, error in services.xml: element \"nosuch\" not allowed here"}`)
}

func TestDeployApplicationPackageErrorWithExpectedFormat(t *testing.T) {
	assertApplicationPackageError(t, 400,
		"Invalid XML, error in services.xml:\nelement \"nosuch\" not allowed here",
		`{
         "error-code": "INVALID_APPLICATION_PACKAGE",
         "message": "Invalid XML, error in services.xml: element \"nosuch\" not allowed here\n"
     }`)
}

func TestDeployError(t *testing.T) {
	assertDeployServerError(t, 501, "Deploy service error")
}

func assertDeploy(applicationPackage string, arguments []string, t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"Success: Deployed "+applicationPackage+"\n",
		executeCommand(t, client, arguments, []string{}))
	assertDeployRequestMade("http://127.0.0.1:19071", client, t)
}

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

func assertApplicationPackageError(t *testing.T, status int, expectedMessage string, returnBody string) {
	client := &mockHttpClient{nextStatus: status, nextBody: returnBody}
	assert.Equal(t,
		"Error: Invalid application package (Status "+strconv.Itoa(status)+")\n\n"+expectedMessage+"\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget/target/application.zip"}, []string{}))
}

func assertDeployServerError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{nextStatus: status, nextBody: errorMessage}
	assert.Equal(t,
		"Error: Error from deploy service at 127.0.0.1:19071 (Status "+strconv.Itoa(status)+"):\n"+errorMessage+"\n",
		executeCommand(t, client, []string{"deploy", "testdata/applications/withTarget/target/application.zip"}, []string{}))
}
