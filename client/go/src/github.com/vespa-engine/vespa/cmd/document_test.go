// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// document command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "github.com/vespa-engine/vespa/utils"
    "io/ioutil"
    "testing"
)

func TestDocumentPut(t *testing.T) {
	assertDocumentPut("mynamespace/music/docid/1", "testdata/A-Head-Full-of-Dreams.json", t)
}

func assertDocumentPut(documentId string, jsonFile string, t *testing.T) {
    reset()
	assert.Equal(t,
	             "\x1b[32mSuccess\n",
	             executeCommand(t, []string{"document", documentId, jsonFile}, []string{}))
    target := getTarget(documentContext).document
    assert.Equal(t, target + "/document/v1/" + documentId, lastRequest.URL.String())
    assert.Equal(t, "application/json", lastRequest.Header.Get("Content-Type"))
    assert.Equal(t, "POST", lastRequest.Method)

    fileContent, _ := ioutil.ReadFile(jsonFile)
    assert.Equal(t, string(fileContent), utils.ReaderToString(lastRequest.Body))
}
