// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// init command tests
// Author: bratseth

package cmd

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func TestClone(t *testing.T) {
	origWd, err := os.Getwd()
	require.Nil(t, err)
	sampleAppName := "text-search"
	app := "mytestapp"
	tempDir := t.TempDir()
	app1 := filepath.Join(tempDir, "app1")
	t.Cleanup(func() {
		os.Chdir(origWd)
		os.RemoveAll(app)
	})
	httpClient := &mock.HTTPClient{}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = httpClient
	testdata, err := os.ReadFile(filepath.Join("testdata", "sample-apps-master.zip"))
	require.Nil(t, err)

	// Initial cloning. GitHub includes the ETag header, but we don't require it
	httpClient.NextResponseBytes(200, testdata)
	require.Nil(t, cli.Run("clone", sampleAppName, app1))
	assert.Equal(t, "Cloned into "+app1+"\n", stdout.String())
	assertFiles(t, app1)

	// Clone with cache hit
	httpClient.NextStatus(http.StatusNotModified)
	stdout.Reset()
	app2 := filepath.Join(tempDir, "app2")
	require.Nil(t, cli.Run("clone", sampleAppName, app2))
	assert.Equal(t, "Using cached sample apps ...\nCloned into "+app2+"\n", stdout.String())
	assertFiles(t, app2)
	stdout.Reset()

	// Clone to current directory (dot)
	emptyDir := filepath.Join(tempDir, "mypath1")
	require.Nil(t, os.Mkdir(emptyDir, 0755))
	require.Nil(t, os.Chdir(emptyDir))
	httpClient.NextStatus(http.StatusNotModified)
	require.Nil(t, cli.Run("clone", sampleAppName, "."))
	assert.Equal(t, "Using cached sample apps ...\nCloned into .\n", stdout.String())
	assertFiles(t, ".")
	stdout.Reset()

	// Clone to non-empty directory
	httpClient.NextStatus(http.StatusNotModified)
	nonEmptyDir := filepath.Join(tempDir, "mypath2")
	require.Nil(t, os.MkdirAll(filepath.Join(nonEmptyDir, "more"), 0755))
	require.NotNil(t, cli.Run("clone", sampleAppName, nonEmptyDir))
	assert.Equal(t, "Error: could not create directory: "+nonEmptyDir+" already exists and is not empty\n", stderr.String())
	stderr.Reset()

	// Clone while ignoring cache
	headers := make(http.Header)
	headers.Set("etag", `W/"id1"`)
	httpClient.NextResponse(mock.HTTPResponse{Status: 200, Body: testdata, Header: headers})
	stdout.Reset()
	app3 := filepath.Join(tempDir, "app3")
	require.Nil(t, cli.Run("clone", "-f", sampleAppName, app3))
	assert.Equal(t, "Cloned into "+app3+"\n", stdout.String())
	assertFiles(t, app3)

	// Cloning falls back to cached copy if GitHub is unavailable
	httpClient.NextStatus(500)
	stdout.Reset()
	app4 := filepath.Join(tempDir, "app4")
	require.Nil(t, cli.Run("clone", "-f=false", sampleAppName, app4))
	assert.Equal(t, "Warning: could not download sample apps: github returned status 500\n", stderr.String())
	assert.Equal(t, "Using cached sample apps ...\nCloned into "+app4+"\n", stdout.String())
	assertFiles(t, app4)

	// The only cached file is the latest one
	dirEntries, err := os.ReadDir(cli.config.cacheDir)
	require.Nil(t, err)
	var zipFiles []string
	for _, de := range dirEntries {
		name := de.Name()
		if strings.HasPrefix(name, sampleAppsNamePrefix) {
			zipFiles = append(zipFiles, name)
		}
	}
	assert.Equal(t, []string{"sample-apps-master_id1.zip"}, zipFiles)
}

func assertFiles(t *testing.T, app string) {
	t.Helper()
	assert.True(t, util.PathExists(filepath.Join(app, "README.md")))
	assert.True(t, util.PathExists(filepath.Join(app, "src", "main", "application")))
	assert.True(t, util.IsDirectory(filepath.Join(app, "src", "main", "application")))

	servicesStat, err := os.Stat(filepath.Join(app, "src", "main", "application", "services.xml"))
	require.Nil(t, err)
	servicesSize := int64(1772)
	assert.Equal(t, servicesSize, servicesStat.Size())

	scriptStat, err := os.Stat(filepath.Join(app, "bin", "convert-msmarco.sh"))
	require.Nil(t, err)
	assert.Equal(t, os.FileMode(0755), scriptStat.Mode())
}
