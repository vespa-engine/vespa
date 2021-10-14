package cmd

import (
	"bytes"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/util"
)

func TestProdInit(t *testing.T) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir)

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
	execute(command{stdin: &buf, homeDir: homeDir, args: []string{"prod", "init", pkgDir}}, t, nil)

	// Verify contents
	deploymentPath := filepath.Join(pkgDir, "src", "main", "application", "deployment.xml")
	deploymentXML := readFileString(t, deploymentPath)
	assert.Contains(t, deploymentXML, `<region active="true">aws-us-west-2a</region>`)
	assert.Contains(t, deploymentXML, `<region active="true">aws-eu-west-1a</region>`)

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
	content, err := ioutil.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func createApplication(t *testing.T, pkgDir string) {
	appDir := filepath.Join(pkgDir, "src", "main", "application")
	targetDir := filepath.Join(pkgDir, "target")
	if err := os.MkdirAll(appDir, 0755); err != nil {
		t.Fatal(err)
	}

	deploymentXML := `<deployment version="1.0">
  <prod>
    <region active="true">aws-us-east-1c</region>
  </prod>
</deployment>`
	if err := ioutil.WriteFile(filepath.Join(appDir, "deployment.xml"), []byte(deploymentXML), 0644); err != nil {
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

	if err := ioutil.WriteFile(filepath.Join(appDir, "services.xml"), []byte(servicesXML), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(targetDir, 0755); err != nil {
		t.Fatal(err)
	}
	if err := ioutil.WriteFile(filepath.Join(pkgDir, "pom.xml"), []byte(""), 0644); err != nil {
		t.Fatal(err)
	}
}

func TestProdSubmit(t *testing.T) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	pkgDir := filepath.Join(t.TempDir(), "app")
	createApplication(t, pkgDir)

	httpClient := &mockHttpClient{}
	httpClient.NextResponse(200, `ok`)
	execute(command{homeDir: homeDir, args: []string{"config", "set", "application", "t1.a1.i1"}}, t, httpClient)
	execute(command{homeDir: homeDir, args: []string{"config", "set", "target", "cloud"}}, t, httpClient)
	execute(command{homeDir: homeDir, args: []string{"api-key"}}, t, httpClient)
	execute(command{homeDir: homeDir, args: []string{"cert", pkgDir}}, t, httpClient)

	// Copy an application package pre-assambled with mvn package
	testAppDir := filepath.Join("testdata", "applications", "withDeployment", "target")
	zipFile := filepath.Join(testAppDir, "application.zip")
	copyFile(t, filepath.Join(pkgDir, "target", "application.zip"), zipFile)
	testZipFile := filepath.Join(testAppDir, "application-test.zip")
	copyFile(t, filepath.Join(pkgDir, "target", "application-test.zip"), testZipFile)

	out, _ := execute(command{homeDir: homeDir, args: []string{"prod", "submit", pkgDir}}, t, httpClient)
	assert.Contains(t, out, "Success: Submitted")
	assert.Contains(t, out, "See https://console.vespa.oath.cloud/tenant/t1/application/a1/prod/deployment for deployment progress")
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
