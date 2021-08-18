// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// init command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "os"
    "testing"
)

func TestInit(t *testing.T) {
    assertCreated("mytestapp", "album-recommendation-selfhosting", t)
}

func assertCreated(destinationName string, sampleAppName string, t *testing.T) {
    reset()
    existingSampleAppsZip = "testdata/sample-apps-master.zip"
    standardOut := executeCommand(t, []string{"init", destinationName, sampleAppName}, []string{})
	defer os.RemoveAll(destinationName)
	assert.Equal(t, "\x1b[32mCreated " + destinationName + "\n", standardOut)
}
