// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

func TestDeploy(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target := LocalTarget(&httpClient)
	appDir, _ := mock.ApplicationPackageDir(t, false, false)
	opts := DeploymentOptions{
		Target:             target,
		ApplicationPackage: ApplicationPackage{Path: appDir},
		HTTPClient:         &httpClient,
	}
	_, err := Deploy(opts)
	assert.Nil(t, err)
	assert.Equal(t, 1, len(httpClient.Requests))
	req := httpClient.LastRequest
	assert.Equal(t, "http://127.0.0.1:19071/application/v2/tenant/default/prepareandactivate", req.URL.String())
	assert.Equal(t, "application/zip", req.Header.Get("content-type"))
	buf := make([]byte, 5)
	req.Body.Read(buf)
	assert.Equal(t, "PK\x03\x04\x14", string(buf))
}

func TestDeployCloud(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target := createCloudTarget(t, "http://vespacloud", io.Discard)
	cloudTarget, ok := target.(*cloudTarget)
	require.True(t, ok)
	cloudTarget.httpClient = &httpClient
	appDir, _ := mock.ApplicationPackageDir(t, false, true)
	opts := DeploymentOptions{
		Target:             target,
		ApplicationPackage: ApplicationPackage{Path: appDir},
		HTTPClient:         &httpClient,
	}
	_, err := Deploy(opts)
	require.Nil(t, err)
	assert.Equal(t, 1, len(httpClient.Requests))
	req := httpClient.LastRequest
	assert.Equal(t, "http://vespacloud/application/v4/tenant/t1/application/a1/instance/i1/deploy/dev-us-north-1", req.URL.String())

	values := parseMultiPart(t, req)
	zipData := values["applicationZip"]
	assert.Equal(t, "PK\x03\x04\x14", string(zipData[:5]))
	_, hasDeployOptions := values["deployOptions"]
	assert.False(t, hasDeployOptions)

	opts.Version = version.MustParse("1.2.3")
	_, err = Deploy(opts)
	require.Nil(t, err)
	req = httpClient.LastRequest
	values = parseMultiPart(t, req)
	zipData = values["applicationZip"]
	assert.Equal(t, "PK\x03\x04\x14", string(zipData[:5]))
	assert.Equal(t, string(values["deployOptions"]), `{"vespaVersion":"1.2.3"}`)
}

func TestApplicationFromString(t *testing.T) {
	app, err := ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)
	assert.Equal(t, ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"}, app)
	_, err = ApplicationFromString("foo")
	assert.NotNil(t, err)

	app, err = ApplicationFromString("t1.a1")
	assert.Nil(t, err)
	assert.Equal(t, ApplicationID{Tenant: "t1", Application: "a1", Instance: "default"}, app)
}

func TestZoneFromString(t *testing.T) {
	zone, err := ZoneFromString("dev.us-north-1")
	assert.Nil(t, err)
	assert.Equal(t, ZoneID{Environment: "dev", Region: "us-north-1"}, zone)
	_, err = ZoneFromString("foo")
	assert.NotNil(t, err)
}

func TestFindApplicationPackage(t *testing.T) {
	dir := t.TempDir()
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath: dir,
		existingFile: filepath.Join(dir, "services.xml"),
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath: filepath.Join(dir, "src", "main", "application"),
		existingFile: filepath.Join(dir, "src", "main", "application") + string(os.PathSeparator),
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath:     filepath.Join(dir, "src", "main", "application"),
		expectedTestPath: filepath.Join(dir, "src", "test", "application"),
		existingFiles:    []string{filepath.Join(dir, "pom.xml"), filepath.Join(dir, "src/test/application/tests/foo.json")},
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		existingFile:     filepath.Join(dir, "pom.xml"),
		requirePackaging: true,
		fail:             true,
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath:     filepath.Join(dir, "target", "application.zip"),
		existingFiles:    []string{filepath.Join(dir, "pom.xml"), filepath.Join(dir, "target", "application.zip")},
		requirePackaging: true,
	})
	dir2 := t.TempDir()
	assertFindApplicationPackage(t, dir2, pkgFixture{
		expectedPath:     dir2,
		expectedTestPath: dir2,
		existingFiles:    []string{filepath.Join(dir2, "services.xml"), filepath.Join(dir2, "tests", "foo.json")},
	})
}

type pkgFixture struct {
	expectedPath     string
	expectedTestPath string
	existingFile     string
	existingFiles    []string
	requirePackaging bool
	fail             bool
}

func assertFindApplicationPackage(t *testing.T, zipOrDir string, fixture pkgFixture) {
	t.Helper()
	if fixture.existingFile != "" {
		writeFile(t, fixture.existingFile)
	}
	for _, f := range fixture.existingFiles {
		writeFile(t, f)
	}
	pkg, err := FindApplicationPackage(zipOrDir, fixture.requirePackaging)
	assert.Equal(t, err != nil, fixture.fail, "Expected error for "+zipOrDir)
	assert.Equal(t, fixture.expectedPath, pkg.Path)
	assert.Equal(t, fixture.expectedTestPath, pkg.TestPath)
}

func writeFile(t *testing.T, name string) {
	t.Helper()
	err := os.MkdirAll(filepath.Dir(name), 0755)
	assert.Nil(t, err)
	if !strings.HasSuffix(name, string(os.PathSeparator)) {
		err = os.WriteFile(name, []byte{0}, 0644)
		assert.Nil(t, err)
	}
}

func parseMultiPart(t *testing.T, req *http.Request) map[string][]byte {
	t.Helper()

	mediaType, params, err := mime.ParseMediaType(req.Header.Get("Content-Type"))
	require.Nil(t, err)
	assert.Equal(t, mediaType, "multipart/form-data")

	values := make(map[string][]byte)
	mr := multipart.NewReader(req.Body, params["boundary"])
	for {
		p, err := mr.NextPart()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		data, err := io.ReadAll(p)
		if err != nil {
			t.Fatal(err)
		}
		values[p.FormName()] = data
	}
	return values
}
