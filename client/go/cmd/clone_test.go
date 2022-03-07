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
	"github.com/vespa-engine/vespa/client/go/mock"
	"github.com/vespa-engine/vespa/client/go/util"
)

func TestClone(t *testing.T) {
	assertCreated("text-search", "mytestapp", t)
}

func assertCreated(sampleAppName string, app string, t *testing.T) {
	appCached := app + "-cache"
	defer os.RemoveAll(app)
	defer os.RemoveAll(appCached)

	httpClient := &mock.HTTPClient{}
	testdata, err := ioutil.ReadFile(filepath.Join("testdata", "sample-apps-master.zip"))
	require.Nil(t, err)
	httpClient.NextResponseBytes(200, testdata)

	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = httpClient
	err = cli.Run("clone", sampleAppName, app)
	assert.Nil(t, err)

	assert.Equal(t, "Created "+app+"\n", stdout.String())
	assertFiles(t, app)

	stdout.Reset()
	err = cli.Run("clone", sampleAppName, appCached)
	assert.Nil(t, err)
	assert.Equal(t, "Using cached sample apps ...\nCreated "+appCached+"\n", stdout.String())
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
