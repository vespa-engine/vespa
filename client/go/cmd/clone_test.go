// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// init command tests
// Author: bratseth

package cmd

import (
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/util"
)

func TestClone(t *testing.T) {
	assertCreated("text-search", "mytestapp", t)
}

func assertCreated(sampleAppName string, app string, t *testing.T) {
	appCached := app + "-cache"
	httpClient := &mockHttpClient{}
	testdata, err := ioutil.ReadFile(filepath.Join("testdata", "sample-apps-master.zip"))
	require.Nil(t, err)
	httpClient.NextResponseBytes(200, testdata)
	cacheDir := t.TempDir()
	require.Nil(t, err)
	defer func() {
		os.RemoveAll(cacheDir)
	}()
	out, _ := execute(command{failTestOnError: true, cacheDir: cacheDir, args: []string{"clone", sampleAppName, app}}, t, httpClient)
	defer os.RemoveAll(app)
	assert.Equal(t, "Created "+app+"\n", out)
	assertFiles(t, app)

	outCached, _ := execute(command{failTestOnError: true, cacheDir: cacheDir, args: []string{"clone", sampleAppName, appCached}}, t, nil)
	defer os.RemoveAll(appCached)
	assert.Equal(t, "Using cached sample apps ...\nCreated "+appCached+"\n", outCached)
	assertFiles(t, appCached)
}

func assertFiles(t *testing.T, app string) {
	assert.True(t, util.PathExists(filepath.Join(app, "README.md")))
	assert.True(t, util.PathExists(filepath.Join(app, "src", "main", "application")))
	assert.True(t, util.IsDirectory(filepath.Join(app, "src", "main", "application")))

	servicesStat, _ := os.Stat(filepath.Join(app, "src", "main", "application", "services.xml"))
	servicesSize := int64(1772)
	assert.Equal(t, servicesSize, servicesStat.Size())

	scriptStat, _ := os.Stat(filepath.Join(app, "bin", "convert-msmarco.sh"))
	assert.Equal(t, os.FileMode(0755), scriptStat.Mode())
}
