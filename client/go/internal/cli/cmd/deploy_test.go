// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// deploy command tests
// Author: bratseth

package cmd

import (
	"archive/zip"
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func TestDeployCloud(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, false, false)

	cli, stdout, stderr := newTestCLI(t, "NO_COLOR=true")
	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `ok`)
	cli.httpClient = httpClient

	app := vespa.ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"}
	assert.Nil(t, cli.Run("config", "set", "application", app.String()))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", "--no-add"))

	stderr.Reset()
	require.NotNil(t, cli.Run("deploy", pkgDir))
	apiKeyWarning := "Warning: Authenticating with API key, intended for use in CI environments.\nHint: Authenticate with 'vespa auth login' instead\n"
	certError := `Error: deployment to Vespa Cloud requires certificate in application package
Hint: See https://cloud.vespa.ai/en/security/guide
Hint: Pass --add-cert to use the certificate of the current application
`
	assert.Equal(t, apiKeyWarning+certError, stderr.String())

	require.Nil(t, cli.Run("deploy", "--add-cert", "--wait=0", pkgDir))
	assert.Contains(t, stdout.String(), "Success: Triggered deployment")

	// Answer interactive certificate copy prompt
	stdout.Reset()
	stderr.Reset()
	cli.isTerminal = func() bool { return true }
	pkgDir2 := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir2, false, false)

	var buf bytes.Buffer
	buf.WriteString("wat\nthe\nfck\nn\n")
	cli.Stdin = &buf
	require.NotNil(t, cli.Run("deploy", "--add-cert=false", "--wait=0", pkgDir2))
	warning := apiKeyWarning + "Warning: Application package does not contain security/clients.pem, which is required for deployments to Vespa Cloud\n"
	assert.Equal(t, warning+strings.Repeat("Error: please answer 'y' or 'n'\n", 3)+certError, stderr.String())
	buf.WriteString("y\n")
	require.Nil(t, cli.Run("deploy", "--add-cert=false", "--wait=0", pkgDir2))
	assert.Contains(t, stdout.String(), "Success: Triggered deployment")

	// Missing application certificate is detected
	stderr.Reset()
	require.NotNil(t, cli.Run("deploy", "--application=t1.a2.i2", pkgDir2))
	assert.Equal(t, apiKeyWarning+"Error: no certificate exists for t1.a2.i2\nHint: Try (re)creating the certificate with 'vespa auth cert'\n", stderr.String())

	// Mismatching certificate is detected
	stdout.Reset()
	stderr.Reset()
	assert.Nil(t, cli.Run("auth", "cert", "--application=t1.a1.i1", "-f", "--no-add"))
	require.NotNil(t, cli.Run("deploy", "--application=t1.a1.i1", pkgDir2))
	assert.Equal(t, apiKeyWarning+`Error: certificate in security/clients.pem does not match the stored key pair for t1.a1.i1
Hint: If this application was deployed using a different application ID in the past, the matching key pair may be stored under a different ID in `+
		cli.config.homeDir+"\nHint: Specify the matching application with --application, or add the current certificate to the package using --add-cert\n",
		stderr.String())
}

func TestDeployCloudFastWait(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, false, false)

	cli, stdout, stderr := newTestCLI(t, "CI=true")
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient

	app := vespa.ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"}
	assert.Nil(t, cli.Run("config", "set", "application", app.String()))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", pkgDir))

	// Deployment completes quickly
	httpClient.NextResponseString(200, `ok`)
	httpClient.NextResponseString(200, `{"active": false, "status": "success"}`)
	require.Nil(t, cli.Run("deploy", pkgDir))
	assert.Contains(t, stdout.String(), "Success: Triggered deployment")
	assert.True(t, httpClient.Consumed())

	// Deployment fails quickly
	stdout.Reset()
	stderr.Reset()
	httpClient.NextResponseString(200, `ok`)
	httpClient.NextResponseString(200, `{"active": false, "status": "unsuccesful"}`)
	require.NotNil(t, cli.Run("deploy", pkgDir))
	assert.Equal(t, stderr.String(), "Error: deployment run 0 not yet complete after waiting up to 3s: aborting wait: deployment failed: run 0 ended with unsuccessful status: unsuccesful\n")
	assert.True(t, httpClient.Consumed())

	// Deployment which is running does not return error
	stdout.Reset()
	stderr.Reset()
	httpClient.NextResponseString(200, `ok`)
	httpClient.NextResponseString(200, `{"active": true, "status": "running"}`)
	require.Nil(t, cli.Run("deploy", pkgDir))
	assert.Contains(t, stdout.String(), "Success: Triggered deployment")
	assert.True(t, httpClient.Consumed())
}

func TestDeployCloudUnauthorized(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, false, false)

	cli, _, stderr := newTestCLI(t, "CI=true")
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient

	app := vespa.ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"}
	assert.Nil(t, cli.Run("config", "set", "application", app.String()))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", pkgDir))
	httpClient.NextResponseString(403, "bugger off")
	require.NotNil(t, cli.Run("deploy", pkgDir))
	assert.Equal(t, `Error: deployment failed: unauthorized (status 403)
bugger off
Hint: You do not have access to the tenant t1
Hint: You may need to create the tenant at https://console.vespa-cloud.com/tenant
Hint: If the tenant already exists you may need to run 'vespa auth login' to gain access to it
`, stderr.String())
}

func TestDeployWait(t *testing.T) {
	cli, stdout, _ := newTestCLI(t)
	client := &mock.HTTPClient{}
	cli.httpClient = client
	cli.retryInterval = 0
	pkg := "testdata/applications/withSource/src/main/application"
	// Deploy service is initially unavailable
	client.NextResponseError(io.EOF)
	client.NextStatus(500)
	client.NextStatus(500)
	// ... then becomes healthy
	client.NextStatus(200)
	// Deployment succeeds
	client.NextResponse(mock.HTTPResponse{
		URI:    "/application/v2/tenant/default/prepareandactivate",
		Status: 200,
		Body:   []byte(`{"session-id": "1"}`),
	})
	mockServiceStatus(client, "foo") // Wait for deployment
	mockServiceStatus(client, "foo") // Look up services
	assert.Nil(t, cli.Run("deploy", "--wait=3", pkg))
	assert.Equal(t,
		"\nSuccess: Deployed '"+pkg+"' with session ID 1\n",
		stdout.String())
}

func TestPrepareZip(t *testing.T) {
	assertPrepare("testdata/applications/withTarget/target/application.zip",
		[]string{"prepare", "testdata/applications/withTarget/target/application.zip"}, t)
}

func TestActivateZip(t *testing.T) {
	assertActivate([]string{"activate", "--wait=0", "testdata/applications/withTarget/target/application.zip"}, t)
}

func TestDeployZip(t *testing.T) {
	assertDeploy("testdata/applications/withTarget/target/application.zip",
		[]string{"deploy", "--wait=0", "testdata/applications/withTarget/target/application.zip"}, t)
}

func TestDeployZipWithURLTargetArgument(t *testing.T) {
	applicationPackage := "testdata/applications/withTarget/target/application.zip"
	arguments := []string{"deploy", "--wait=0", "testdata/applications/withTarget/target/application.zip", "-t", "http://target:19071"}

	client := &mock.HTTPClient{}
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	assert.Nil(t, cli.Run(arguments...))
	assert.Equal(t,
		"\nSuccess: Deployed '"+applicationPackage+"' with session ID 0\n",
		stdout.String())
	assertDeployRequestMade("http://target:19071", client, t)
}

func TestDeployZipWithLocalTargetArgument(t *testing.T) {
	assertDeploy("testdata/applications/withTarget/target/application.zip",
		[]string{"deploy", "--wait=0", "testdata/applications/withTarget/target/application.zip", "-t", "local"}, t)
}

func TestDeploySourceDirectory(t *testing.T) {
	assertDeploy("testdata/applications/withSource/src/main/application",
		[]string{"deploy", "--wait=0", "testdata/applications/withSource/src/main/application"}, t)
}

func TestDeployApplicationDirectoryWithSource(t *testing.T) {
	assertDeploy("testdata/applications/withSource/src/main/application",
		[]string{"deploy", "--wait=0", "testdata/applications/withSource"}, t)
}

func TestDeployApplicationDirectoryWithPomAndTarget(t *testing.T) {
	assertDeploy("testdata/applications/withTarget/target/application",
		[]string{"deploy", "--wait=0", "testdata/applications/withTarget"}, t)
}

func TestDeployApplicationDirectoryWithPomAndEmptyTarget(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	assert.NotNil(t, cli.Run("deploy", "--wait=0", "testdata/applications/withEmptyTarget"))
	assert.Equal(t,
		"Error: found pom.xml, but testdata/applications/withEmptyTarget/target/application does not exist: run 'mvn package' first\n",
		stderr.String())
}

func TestDeployIncludesExpectedFiles(t *testing.T) {
	cli, stdout, _ := newTestCLI(t)
	client := &mock.HTTPClient{}
	cli.httpClient = client
	assert.Nil(t, cli.Run("deploy", "--wait=0", "testdata/applications/withSource"))
	applicationPackage := "testdata/applications/withSource/src/main/application"
	assert.Equal(t,
		"\nSuccess: Deployed '"+applicationPackage+"' with session ID 0\n",
		stdout.String())

	zipName := filepath.Join(t.TempDir(), "tmp.zip")
	f, err := os.Create(zipName)
	assert.Nil(t, err)
	if _, err := io.Copy(f, client.LastRequest.Body); err != nil {
		t.Fatal(err)
	}
	zr, err := zip.OpenReader(zipName)
	assert.Nil(t, err)
	defer zr.Close()
	var zipFiles []string
	for _, f := range zr.File {
		zipFiles = append(zipFiles, f.Name)
	}
	assert.Equal(t, []string{".vespaignore", "hosts.xml", "schemas/msmarco.sd", "services.xml"}, zipFiles)
}

func TestDeployApplicationPackageErrorWithUnexpectedNonJson(t *testing.T) {
	assertApplicationPackageError(t, "deploy", 400,
		"Raw text error",
		"Raw text error")
}

func TestDeployApplicationPackageErrorWithUnexpectedJson(t *testing.T) {
	assertApplicationPackageError(t, "deploy", 400,
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
	t.Helper()
	cli, stdout, _ := newTestCLI(t)
	client := &mock.HTTPClient{}
	cli.httpClient = client
	assert.Nil(t, cli.Run(arguments...))
	assert.Equal(t,
		"\nSuccess: Deployed '"+applicationPackage+"' with session ID 0\n",
		stdout.String())
	assertDeployRequestMade("http://127.0.0.1:19071", client, t)
}

func assertPrepare(applicationPackage string, arguments []string, t *testing.T) {
	t.Helper()
	client := &mock.HTTPClient{}
	client.NextResponseString(200, `{"session-id":"42"}`)
	client.NextResponseString(200, `{"session-id":"42","message":"Session 42 for tenant 'default' prepared.","log":[{"level":"WARNING","message":"Warning message 1","time": 1430134091319}]}`)
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	assert.Nil(t, cli.Run(arguments...))
	assert.Equal(t,
		"Success: Prepared '"+applicationPackage+"' with session 42\n",
		stdout.String())

	assertPackageUpload(0, "http://127.0.0.1:19071/application/v2/tenant/default/session", client, t)
	sessionURL := "http://127.0.0.1:19071/application/v2/tenant/default/session/42/prepared"
	assert.Equal(t, sessionURL, client.Requests[1].URL.String())
	assert.Equal(t, "PUT", client.Requests[1].Method)
}

func assertActivate(arguments []string, t *testing.T) {
	t.Helper()
	client := &mock.HTTPClient{}
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	if err := cli.config.writeSessionID(vespa.DefaultApplication, 42); err != nil {
		t.Fatal(err)
	}
	assert.Nil(t, cli.Run(arguments...))
	assert.Equal(t,
		"Success: Activated application with session 42\n",
		stdout.String())
	url := "http://127.0.0.1:19071/application/v2/tenant/default/session/42/active"
	assert.Equal(t, url, client.LastRequest.URL.String())
	assert.Equal(t, "PUT", client.LastRequest.Method)
}

func assertPackageUpload(requestNumber int, url string, client *mock.HTTPClient, t *testing.T) {
	t.Helper()
	req := client.LastRequest
	if requestNumber >= 0 {
		req = client.Requests[requestNumber]
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

func assertDeployRequestMade(target string, client *mock.HTTPClient, t *testing.T) {
	t.Helper()
	assertPackageUpload(-1, target+"/application/v2/tenant/default/prepareandactivate", client, t)
}

func assertApplicationPackageError(t *testing.T, cmd string, status int, expectedMessage string, returnBody string) {
	t.Helper()
	client := &mock.HTTPClient{}
	client.NextResponseString(status, returnBody)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	args := []string{}
	args = append(args, cmd)
	switch cmd {
	case "activate", "deploy":
		args = append(args, "--wait=0")
	}
	args = append(args, "testdata/applications/withTarget/target/application.zip")
	assert.NotNil(t, cli.Run(args...))
	assert.Equal(t,
		"Error: invalid application package (status "+strconv.Itoa(status)+")\n"+expectedMessage+"\n",
		stderr.String())
}

func assertDeployServerError(t *testing.T, status int, errorMessage string) {
	t.Helper()
	client := &mock.HTTPClient{}
	client.NextResponseString(status, errorMessage)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("deploy", "--wait=0", "testdata/applications/withTarget/target/application.zip"))
	assert.Equal(t,
		"Error: error from deploy API at 127.0.0.1:19071 (status "+strconv.Itoa(status)+"):\n"+errorMessage+"\n",
		stderr.String())
}
