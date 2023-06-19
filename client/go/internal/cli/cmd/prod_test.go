package cmd

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/util"
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
	assert.Nil(t, cli.Run("prod", "init", pkgDir))

	// Verify contents
	deploymentPath := filepath.Join(pkgDir, "src", "main", "application", "deployment.xml")
	deploymentXML := readFileString(t, deploymentPath)
	assert.Contains(t, deploymentXML, `<region>aws-us-west-2a</region>`)
	assert.Contains(t, deploymentXML, `<region>aws-eu-west-1a</region>`)

	servicesPath := filepath.Join(pkgDir, "src", "main", "application", "services.xml")
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
	assert.True(t, util.PathExists(deploymentPath+".1.bak"))
	assert.True(t, util.PathExists(servicesPath+".1.bak"))
}

func readFileString(t *testing.T, filename string) string {
	content, err := os.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func createApplication(t *testing.T, pkgDir string, java bool, skipTests bool) {
	appDir := filepath.Join(pkgDir, "src", "main", "application")
	targetDir := filepath.Join(pkgDir, "target")
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
	if err := os.MkdirAll(targetDir, 0755); err != nil {
		t.Fatal(err)
	}
	if java {
		if skipTests {
			t.Fatalf("skipTests=%t has no effect when java=%t", skipTests, java)
		}
		if err := os.WriteFile(filepath.Join(pkgDir, "pom.xml"), []byte(""), 0644); err != nil {
			t.Fatal(err)
		}
	} else if !skipTests {
		testsDir := filepath.Join(pkgDir, "src", "test", "application", "tests")
		testBytes, _ := io.ReadAll(strings.NewReader("{\"steps\":[{}]}"))
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
	httpClient.NextResponseString(200, `ok`)

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
	assert.Nil(t, cli.Run("prod", "deploy", "--add-cert"))
	assert.Contains(t, stdout.String(), "Success: Deployed")
	assert.Contains(t, stdout.String(), "See https://console.vespa-cloud.com/tenant/t1/application/a1/prod/deployment for deployment progress")
	stdout.Reset()
	assert.Nil(t, cli.Run("prod", "submit", "--add-cert")) // old variant also works
	assert.Contains(t, stdout.String(), "Success: Deployed")
	assert.Contains(t, stdout.String(), "See https://console.vespa-cloud.com/tenant/t1/application/a1/prod/deployment for deployment progress")
}

func TestProdDeployWithJava(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, true, false)

	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `ok`)
	cli, stdout, _ := newTestCLI(t, "CI=true")
	cli.httpClient = httpClient
	assert.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", "--no-add"))

	// Copy an application package pre-assembled with mvn package
	testAppDir := filepath.Join("testdata", "applications", "withDeployment", "target")
	zipFile := filepath.Join(testAppDir, "application.zip")
	copyFile(t, filepath.Join(pkgDir, "target", "application.zip"), zipFile)
	testZipFile := filepath.Join(testAppDir, "application-test.zip")
	copyFile(t, filepath.Join(pkgDir, "target", "application-test.zip"), testZipFile)

	stdout.Reset()
	cli.Environment["VESPA_CLI_API_KEY_FILE"] = filepath.Join(cli.config.homeDir, "t1.api-key.pem")
	assert.Nil(t, cli.Run("prod", "deploy", pkgDir))
	assert.Contains(t, stdout.String(), "Success: Deployed")
	assert.Contains(t, stdout.String(), "See https://console.vespa-cloud.com/tenant/t1/application/a1/prod/deployment for deployment progress")
}

func TestProdDeployInvalidZip(t *testing.T) {
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir, true, false)

	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `ok`)
	cli, _, stderr := newTestCLI(t, "CI=true")
	cli.httpClient = httpClient
	assert.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", "--no-add"))

	// Copy an invalid application package containing relative file names
	testAppDir := filepath.Join("testdata", "applications", "withInvalidEntries", "target")
	zipFile := filepath.Join(testAppDir, "application.zip")
	copyFile(t, filepath.Join(pkgDir, "target", "application.zip"), zipFile)
	testZipFile := filepath.Join(testAppDir, "application-test.zip")
	copyFile(t, filepath.Join(pkgDir, "target", "application-test.zip"), testZipFile)

	assert.NotNil(t, cli.Run("prod", "deploy", pkgDir))
	assert.Equal(t, "Error: found invalid path inside zip: ../../../../../../../tmp/foo\n", stderr.String())
}

func copyFile(t *testing.T, dstFilename, srcFilename string) {
	dst, err := os.Create(dstFilename)
	if err != nil {
		t.Fatal(err)
	}
	defer dst.Close()
	src, err := os.Open(srcFilename)
	if err != nil {
		t.Fatal(err)
	}
	defer src.Close()
	if _, err := io.Copy(dst, src); err != nil {
		t.Fatal(err)
	}
}
