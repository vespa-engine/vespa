// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func TestProdInit(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, false, false)

	answers := []string{
		// Regions
		"invalid input",
		"aws-us-west-2a,aws-eu-west-1a",

		// Node count: qrs
		"invalid input",
		"4",

		// Node resources: qrs
		"invalid input",
		"auto",

		// Node count: music
		"invalid input",
		"6",

		// Node resources: music
		"invalid input",
		"vcpu=16,memory=64Gb,disk=100Gb",
	}
	var buf bytes.Buffer
	buf.WriteString(strings.Join(answers, "\n") + "\n")

	cli, _, _ := newTestCLI(t)
	cli.Stdin = &buf
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("config", "set", "application", "foo.bar"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("prod", "init", pkgDir))

	// Verify contents
	deploymentPath := filepath.Join(pkgDir, "deployment.xml")
	deploymentXML := readFileString(t, deploymentPath)
	assert.Contains(t, deploymentXML, `<region>aws-us-west-2a</region>`)
	assert.Contains(t, deploymentXML, `<region>aws-eu-west-1a</region>`)

	servicesPath := filepath.Join(pkgDir, "services.xml")
	servicesXML := readFileString(t, servicesPath)
	containerFragment := `<container id="qrs" version="1.0">
    <document-api></document-api>
    <search></search>
    <nodes count="4"></nodes>
  </container>`
	assert.Contains(t, servicesXML, containerFragment)
	contentFragment := `<content id="music" version="1.0">
    <redundancy>2</redundancy>
    <documents>
      <document type="music" mode="index"></document>
    </documents>
    <nodes count="6">
      <resources vcpu="16" memory="64Gb" disk="100Gb"></resources>
    </nodes>
  </content>`
	assert.Contains(t, servicesXML, contentFragment)

	// Backups are created
	assert.True(t, ioutil.Exists(deploymentPath+".1.bak"))
	assert.True(t, ioutil.Exists(servicesPath+".1.bak"))
}

func readFileString(t *testing.T, filename string) string {
	t.Helper()
	content, err := os.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func createApplication(t *testing.T, pkgDir string, java bool, skipTests bool) {
	appDir := pkgDir
	testsDir := pkgDir
	if java {
		appDir = filepath.Join(pkgDir, "target", "application")
		testsDir = filepath.Join(pkgDir, "target", "application-test")
	}
	if err := os.MkdirAll(appDir, 0755); err != nil {
		t.Fatal(err)
	}
	deploymentXML := `<deployment version="1.0">
  <prod>
    <region>aws-us-east-1c</region>
  </prod>
</deployment>`
	if err := os.WriteFile(filepath.Join(appDir, "deployment.xml"), []byte(deploymentXML), 0644); err != nil {
		t.Fatal(err)
	}
	servicesXML := `<services version="1.0" xmlns:deploy="vespa" xmlns:preprocess="properties">
  <container id="qrs" version="1.0">
    <document-api/>
    <search/>
    <nodes count="2">
      <resources vcpu="4" memory="8Gb" disk="100Gb"/>
    </nodes>
  </container>
  <content id="music" version="1.0">
    <redundancy>2</redundancy>
    <documents>
      <document type="music" mode="index"></document>
    </documents>
    <nodes count="4"></nodes>
  </content>
</services>`

	if err := os.WriteFile(filepath.Join(appDir, "services.xml"), []byte(servicesXML), 0644); err != nil {
		t.Fatal(err)
	}
	if java {
		if err := os.WriteFile(filepath.Join(pkgDir, "pom.xml"), []byte(""), 0644); err != nil {
			t.Fatal(err)
		}
	}
	if !skipTests {
		if err := os.MkdirAll(testsDir, 0755); err != nil {
			t.Fatal(err)
		}
		testBytes := []byte("{\"steps\":[{}]}")
		writeTest(filepath.Join(testsDir, "system-test", "test.json"), testBytes, t)
		writeTest(filepath.Join(testsDir, "staging-setup", "test.json"), testBytes, t)
		writeTest(filepath.Join(testsDir, "staging-test", "test.json"), testBytes, t)
	}
}

func writeTest(path string, content []byte, t *testing.T) {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, content, 0644); err != nil {
		t.Fatal(err)
	}
}

func TestProdDeploy(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, false, false)
	prodDeploy(pkgDir, t)
}

func TestProdDeployWithoutTests(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, false, true)
	prodDeploy(pkgDir, t)
}

func prodDeploy(pkgDir string, t *testing.T) {
	t.Helper()
	httpClient := &mock.HTTPClient{}

	cli, stdout, _ := newTestCLI(t, "CI=true")
	cli.httpClient = httpClient
	app := vespa.ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"}
	assert.Nil(t, cli.Run("config", "set", "application", app.String()))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", "--no-add"))

	// Zipping requires relative paths, so must let command run from pkgDir, then reset cwd for subsequent tests.
	if cwd, err := os.Getwd(); err != nil {
		t.Fatal(err)
	} else {
		defer os.Chdir(cwd)
	}
	if err := os.Chdir(pkgDir); err != nil {
		t.Fatal(err)
	}

	stdout.Reset()
	cli.Environment["VESPA_CLI_API_KEY_FILE"] = filepath.Join(cli.config.homeDir, "t1.api-key.pem")
	httpClient.NextResponseString(200, `{"build": 42}`)
	assert.Nil(t, cli.Run("prod", "deploy", "--add-cert"))
	assert.Contains(t, stdout.String(), "Success: Deployed '.' with build number 42")
	assert.Contains(t, stdout.String(), "See https://console.vespa-cloud.com/tenant/t1/application/a1/prod/deployment for deployment progress")
	stdout.Reset()
	httpClient.NextResponseString(200, `{"build": 43}`)
	assert.Nil(t, cli.Run("prod", "submit", "--add-cert")) // old variant also works
	assert.Contains(t, stdout.String(), "Success: Deployed '.' with build number 43")
	assert.Contains(t, stdout.String(), "See https://console.vespa-cloud.com/tenant/t1/application/a1/prod/deployment for deployment progress")
}

func TestProdDeployWithJava(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, true, false)

	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `{"build": 42}`)
	cli, stdout, stderr := newTestCLI(t, "CI=true")
	cli.httpClient = httpClient
	assert.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", "--no-add"))

	stdout.Reset()
	cli.Environment["VESPA_CLI_API_KEY_FILE"] = filepath.Join(cli.config.homeDir, "t1.api-key.pem")
	assert.Nil(t, cli.Run("prod", "deploy", "--add-cert", pkgDir))
	assert.Equal(t, "", stderr.String())
	assert.Contains(t, stdout.String(), "Success: Deployed '"+pkgDir+"/target/application' with build number 42")
	assert.Contains(t, stdout.String(), "See https://console.vespa-cloud.com/tenant/t1/application/a1/prod/deployment for deployment progress")
}

func TestProdDeployInvalidZip(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, true, false)

	httpClient := &mock.HTTPClient{}
	cli, _, stderr := newTestCLI(t, "CI=true")
	cli.httpClient = httpClient
	assert.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", "--no-add"))

	// Copy an invalid application package containing relative file names
	testAppDir := filepath.Join("testdata", "applications", "withInvalidEntries", "target")
	zipFile := filepath.Join(testAppDir, "application.zip")

	assert.NotNil(t, cli.Run("prod", "deploy", zipFile))
	assert.Equal(t, "Error: found invalid path inside zip: ../../../../../../../tmp/foo\n", stderr.String())
}
