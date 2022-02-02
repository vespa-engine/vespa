// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// init command tests
// Author: bratseth

package cmd

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/util"
)

func TestClone(t *testing.T) {
	assertCreated("text-search", "mytestapp", t)
}

func assertCreated(sampleAppName string, app string, t *testing.T) {
	testFile := filepath.Join("testdata", "sample-apps-master.zip")
	now := time.Now()
	if err := os.Chtimes(testFile, now, now); err != nil { // Ensure test file is considered new enough by cache mechanism
		t.Fatal(err)
	}
	out, _ := execute(command{cacheDir: filepath.Dir(testFile), args: []string{"clone", sampleAppName, app}}, t, nil)
	defer os.RemoveAll(app)
	assert.Equal(t, "Using cached sample apps ...\nCreated "+app+"\n", out)
	assert.True(t, util.PathExists(filepath.Join(app, "README.md")))
	assert.True(t, util.PathExists(filepath.Join(app, "src", "main", "application")))
	assert.True(t, util.IsDirectory(filepath.Join(app, "src", "main", "application")))

	servicesStat, _ := os.Stat(filepath.Join(app, "src", "main", "application", "services.xml"))
	servicesSize := int64(1772)
	assert.Equal(t, servicesSize, servicesStat.Size())

	scriptStat, _ := os.Stat(filepath.Join(app, "bin", "convert-msmarco.sh"))
	assert.Equal(t, os.FileMode(0755), scriptStat.Mode())
}
