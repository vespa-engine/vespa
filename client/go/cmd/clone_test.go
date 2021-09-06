// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// init command tests
// Author: bratseth

package cmd

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/util"
)

func TestClone(t *testing.T) {
	assertCreated("album-recommendation-selfhosted", "mytestapp", t)
}

func assertCreated(sampleAppName string, app string, t *testing.T) {
	existingSampleAppsZip = "testdata/sample-apps-master.zip"
	standardOut := executeCommand(t, &mockHttpClient{}, []string{"clone", sampleAppName, app}, []string{})
	defer os.RemoveAll(app)
	assert.Equal(t, "Created "+app+"\n", standardOut)
	assert.True(t, util.PathExists(filepath.Join(app, "README.md")))
	assert.True(t, util.PathExists(filepath.Join(app, "src", "main", "application")))
	assert.True(t, util.IsDirectory(filepath.Join(app, "src", "main", "application")))

	servicesStat, _ := os.Stat(filepath.Join(app, "src", "main", "application", "services.xml"))
	servicesSize := int64(2474)
	assert.Equal(t, servicesSize, servicesStat.Size())
}
