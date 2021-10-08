// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// deploy command tests
// Author: bratseth

package cmd

import (
	"path/filepath"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func TestPrepareZip(t *testing.T) {
	assertPrepare("testdata/applications/withTarget/target/application.zip",
		[]string{"prepare", "testdata/applications/withTarget/target/application.zip"}, t)
}

func TestActivateZip(t *testing.T) {
	assertActivate("testdata/applications/withTarget/target/application.zip",
		[]string{"activate", "testdata/applications/withTarget/target/application.zip"}, t)
}

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
	_, outErr := execute(command{args: []string{"deploy", "testdata/applications/withEmptyTarget"}}, t, client)
	assert.Equal(t,
		"Error: pom.xml exists but no target/application.zip. Run mvn package first\n",
		outErr)
}

func TestDeployApplicationPackageErrorWithUnexpectedNonJson(t *testing.T) {
	assertApplicationPackageError(t, "deploy", 401,
		"Raw text error",
		"Raw text error")
}

func TestDeployApplicationPackageErrorWithUnexpectedJson(t *testing.T) {
	assertApplicationPackageError(t, "deploy", 401,
		`{
    "some-unexpected-json": "Invalid XML, error in services.xml: element \"nosuch\" not allowed here"
}`,
		`{"some-unexpected-json": "Invalid XML, error in services.xml: element \"nosuch\" not allowed here"}`)
}

func TestDeployApplicationPackageErrorWithExpectedFormat(t *testing.T) {
	assertApplicationPackageError(t, "deploy", 400,
		"Invalid XML, error in services.xml:\nelement \"nosuch\" not allowed here",
		`{
         "error-code": "INVALID_APPLICATION_PACKAGE",
         "message": "Invalid XML, error in services.xml: element \"nosuch\" not allowed here"
     }`)
}

func TestPrepareApplicationPackageErrorWithExpectedFormat(t *testing.T) {
	assertApplicationPackageError(t, "prepare", 400,
		"Invalid XML, error in services.xml:\nelement \"nosuch\" not allowed here",
		`{
         "error-code": "INVALID_APPLICATION_PACKAGE",
         "message": "Invalid XML, error in services.xml: element \"nosuch\" not allowed here"
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

func assertPrepare(applicationPackage string, arguments []string, t *testing.T) {
	client := &mockHttpClient{}
	client.NextResponse(200, `{"session-id":"42"}`)
	assert.Equal(t,
		"Success: Prepared "+applicationPackage+" with session 42\n",
		executeCommand(t, client, arguments, []string{}))

	assertPackageUpload(0, "http://127.0.0.1:19071/application/v2/tenant/default/session", client, t)
	sessionURL := "http://127.0.0.1:19071/application/v2/tenant/default/session/42/prepared"
	assert.Equal(t, sessionURL, client.requests[1].URL.String())
	assert.Equal(t, "PUT", client.requests[1].Method)
}

func assertActivate(applicationPackage string, arguments []string, t *testing.T) {
	client := &mockHttpClient{}
	homeDir := t.TempDir()
	cfg := Config{Home: filepath.Join(homeDir, ".vespa"), createDirs: true}
	if err := cfg.WriteSessionID(vespa.DefaultApplication, 42); err != nil {
		t.Fatal(err)
	}
	out, _ := execute(command{args: arguments, homeDir: cfg.Home}, t, client)
	assert.Equal(t,
		"Success: Activated "+applicationPackage+" with session 42\n",
		out)
	url := "http://127.0.0.1:19071/application/v2/tenant/default/session/42/active"
	assert.Equal(t, url, client.lastRequest.URL.String())
	assert.Equal(t, "PUT", client.lastRequest.Method)
}

func assertPackageUpload(requestNumber int, url string, client *mockHttpClient, t *testing.T) {
	req := client.lastRequest
	if requestNumber >= 0 {
		req = client.requests[requestNumber]
	}
	assert.Equal(t, url, req.URL.String())
	assert.Equal(t, "application/zip", req.Header.Get("Content-Type"))
	assert.Equal(t, "POST", req.Method)
	var body = req.Body
	assert.NotNil(t, body)
	buf := make([]byte, 7) // Just check the first few bytes
	body.Read(buf)
	assert.Equal(t, "PK\x03\x04\x14\x00\b", string(buf))
}

func assertDeployRequestMade(target string, client *mockHttpClient, t *testing.T) {
	assertPackageUpload(-1, target+"/application/v2/tenant/default/prepareandactivate", client, t)
}

func assertApplicationPackageError(t *testing.T, cmd string, status int, expectedMessage string, returnBody string) {
	client := &mockHttpClient{}
	client.NextResponse(status, returnBody)
	_, outErr := execute(command{args: []string{cmd, "testdata/applications/withTarget/target/application.zip"}}, t, client)
	assert.Equal(t,
		"Error: Invalid application package (Status "+strconv.Itoa(status)+")\n\n"+expectedMessage+"\n",
		outErr)
}

func assertDeployServerError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{}
	client.NextResponse(status, errorMessage)
	_, outErr := execute(command{args: []string{"deploy", "testdata/applications/withTarget/target/application.zip"}}, t, client)
	assert.Equal(t,
		"Error: Error from deploy service at 127.0.0.1:19071 (Status "+strconv.Itoa(status)+"):\n"+errorMessage+"\n",
		outErr)
}
