// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"archive/zip"
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
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

func TestDeploy(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target := LocalTarget(&httpClient, TLSOptions{}, 0)
	appDir, _ := mock.ApplicationPackageDir(t, false, false)
	opts := DeploymentOptions{
		Target:             target,
		ApplicationPackage: ApplicationPackage{Path: appDir},
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
	target, _ := createCloudTarget(t, io.Discard)
	cloudTarget, ok := target.(*cloudTarget)
	require.True(t, ok)
	cloudTarget.httpClient = &httpClient
	appDir, _ := mock.ApplicationPackageDir(t, false, true)
	opts := DeploymentOptions{
		Target:             target,
		ApplicationPackage: ApplicationPackage{Path: appDir},
	}
	_, err := Deploy(opts)
	require.Nil(t, err)
	assert.Equal(t, 1, len(httpClient.Requests))
	req := httpClient.LastRequest
	assert.Equal(t, "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/t1/application/a1/instance/i1/deploy/dev-us-north-1", req.URL.String())

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

func TestSubmit(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target, _ := createCloudTarget(t, io.Discard)
	cloudTarget, ok := target.(*cloudTarget)
	require.True(t, ok)
	cloudTarget.httpClient = &httpClient
	appDir, _ := mock.ApplicationPackageDir(t, false, true)
	opts := DeploymentOptions{
		Target:             target,
		ApplicationPackage: ApplicationPackage{Path: appDir},
	}
	httpClient.NextResponseString(200, `{"build": 42}`)
	build, err := Submit(opts, Submission{})
	require.Nil(t, err)
	require.Equal(t, int64(42), build)
	require.Nil(t, httpClient.LastRequest.ParseMultipartForm(1<<20))
	assert.Equal(t, "{}", httpClient.LastRequest.FormValue("submitOptions"))
	f, err := httpClient.LastRequest.MultipartForm.File["applicationZip"][0].Open()
	require.Nil(t, err)
	defer f.Close()
	contents := make([]byte, 5)
	f.Read(contents)
	assert.Equal(t, "PK\x03\x04\x14", string(contents))

	httpClient.NextResponseString(200, `{"build": 43}`)
	build, err = Submit(opts, Submission{
		Risk:        1,
		Commit:      "sha",
		Description: "broken garbage",
		AuthorEmail: "foo@example.com",
		SourceURL:   "https://github.com/foo/repo",
	})
	require.Nil(t, err)
	require.Equal(t, int64(43), build)
	require.Nil(t, httpClient.LastRequest.ParseMultipartForm(1<<20))
	assert.Equal(t, "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/t1/application/a1/submit", httpClient.LastRequest.URL.String())
	assert.Equal(t,
		"{\"risk\":1,\"commit\":\"sha\",\"description\":\"broken garbage\",\"authorEmail\":\"foo@example.com\",\"sourceUrl\":\"https://github.com/foo/repo\"}",
		httpClient.LastRequest.FormValue("submitOptions"))
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
		expectedPath:     dir,
		expectedTestPath: dir,
		existingFiles:    []string{filepath.Join(dir, "services.xml"), filepath.Join(dir, "tests", "foo.json")},
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
		existingFile: filepath.Join(dir, "pom.xml"),
		compiled:     true,
		fail:         true,
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath:  filepath.Join(dir, "target", "application"),
		existingFiles: []string{filepath.Join(dir, "target", "application"), filepath.Join(dir, "target", "application.zip")},
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath:     filepath.Join(dir, "target", "application"),
		expectedTestPath: filepath.Join(dir, "target", "application-test"),
		existingFiles:    []string{filepath.Join(dir, "target", "application"), filepath.Join(dir, "target", "application-test")},
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath:     filepath.Join(dir, "src", "main", "application"),
		expectedTestPath: filepath.Join(dir, "src", "test", "application"),
		existingFiles:    []string{filepath.Join(dir, "target", "application"), filepath.Join(dir, "target", "application-test")},
		sourceOnly:       true,
	})
	zip := filepath.Join(dir, "myapp.zip")
	assertFindApplicationPackage(t, zip, pkgFixture{
		expectedPath: zip,
	})
}

func TestDeactivate(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target := LocalTarget(&httpClient, TLSOptions{}, 0)
	opts := DeploymentOptions{Target: target}
	require.Nil(t, Deactivate(opts))
	assert.Equal(t, 1, len(httpClient.Requests))
	req := httpClient.LastRequest
	assert.Equal(t, "DELETE", req.Method)
	assert.Equal(t, "http://127.0.0.1:19071/application/v2/tenant/default/application/default", req.URL.String())
}

func TestDeactivateCloud(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target, _ := createCloudTarget(t, io.Discard)
	cloudTarget, ok := target.(*cloudTarget)
	require.True(t, ok)
	cloudTarget.httpClient = &httpClient
	opts := DeploymentOptions{Target: target}
	require.Nil(t, Deactivate(opts))
	assert.Equal(t, 1, len(httpClient.Requests))
	req := httpClient.LastRequest
	assert.Equal(t, "DELETE", req.Method)
	assert.Equal(t, "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1", req.URL.String())
}

func TestFetch(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target := LocalTarget(&httpClient, TLSOptions{}, 0)
	opts := DeploymentOptions{Target: target}
	httpClient.NextResponse(mock.HTTPResponse{
		URI:    "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/content",
		Status: 200,
		Body: []byte(`[
"/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/content/schemas/",
"/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/content/services.xml"
]`),
	})
	httpClient.NextResponse(mock.HTTPResponse{
		URI:    "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/content/schemas/",
		Status: 200,
		Body: []byte(`[
"/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/content/schemas/music.sd"
]`),
	})
	httpClient.NextResponse(mock.HTTPResponse{
		URI:    "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/content/schemas/music.sd",
		Status: 200,
		Body:   []byte(`music.sd contents`),
	})
	httpClient.NextResponse(mock.HTTPResponse{
		URI:    "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/content/services.xml",
		Status: 200,
		Body:   []byte(`services.xml contents`),
	})
	dir := t.TempDir()
	dst, err := Fetch(opts, dir)
	require.Nil(t, err)
	assert.True(t, ioutil.Exists(dst))

	f, err := os.Open(dst)
	require.Nil(t, err)
	defer f.Close()
	zr, err := zip.NewReader(f, 1000)
	require.Nil(t, err)
	schema, err := zr.Open("schemas/music.sd")
	require.Nil(t, err)
	data, err := io.ReadAll(schema)
	require.Nil(t, err)
	assert.Equal(t, `music.sd contents`, string(data))
}

func TestFetchCloud(t *testing.T) {
	httpClient := mock.HTTPClient{}
	target, _ := createCloudTarget(t, io.Discard)
	cloudTarget, ok := target.(*cloudTarget)
	require.True(t, ok)
	cloudTarget.httpClient = &httpClient
	opts := DeploymentOptions{Target: target}
	httpClient.NextResponse(mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/package",
		Status: 200,
		Body:   []byte(`application zip`),
	})
	dir := t.TempDir()
	dst, err := Fetch(opts, dir)
	require.Nil(t, err)
	assert.True(t, ioutil.Exists(dst))
}

type pkgFixture struct {
	expectedPath     string
	expectedTestPath string
	existingFile     string
	existingFiles    []string
	compiled         bool
	sourceOnly       bool
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
	pkg, err := FindApplicationPackage(zipOrDir, PackageOptions{Compiled: fixture.compiled, SourceOnly: fixture.sourceOnly})
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
