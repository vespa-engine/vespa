// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// document command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "github.com/vespa-engine/vespa/util"
    "io/ioutil"
    "strconv"
    "testing"
)

func TestDocumentPostWithIdArg(t *testing.T) {
	assertDocumentPost("mynamespace/music/docid/1", "testdata/A-Head-Full-of-Dreams.json", t)
}

func TestDocumentPostWithIdInDocument(t *testing.T) {
	assertDocumentPost("", "testdata/A-Head-Full-of-Dreams-With-Id.json", t)
}

func TestDocumentPostDocumentError(t *testing.T) {
	assertDocumentError(t, 401, "Document error")
}

func TestDocumentPostServerError(t *testing.T) {
	assertDocumentServerError(t, 501, "Server error")
}

func assertDocumentPost(documentId string, jsonFile string, t *testing.T) {
    client := &mockHttpClient{}
	assert.Equal(t,
	             "\x1b[32mSuccess\n",
	             executeCommand(t, client, []string{"document", "post", documentId, jsonFile}, []string{}))
    target := getTarget(documentContext).document
    assert.Equal(t, target + "/document/v1/" + documentId, client.lastRequest.URL.String())
    assert.Equal(t, "application/json", client.lastRequest.Header.Get("Content-Type"))
    assert.Equal(t, "POST", client.lastRequest.Method)

    fileContent, _ := ioutil.ReadFile(jsonFile)
    assert.Equal(t, string(fileContent), util.ReaderToString(client.lastRequest.Body))
}

func assertDocumentError(t *testing.T, status int, errorMessage string) {
    client := &mockHttpClient{ nextStatus: status, nextBody: errorMessage, }
	assert.Equal(t,
	             "\x1b[31mInvalid document (Status " + strconv.Itoa(status) + "):\n" + errorMessage + "\n",
	             executeCommand(t, client, []string{"document", "post",
	                                                "mynamespace/music/docid/1",
	                                                "testdata/A-Head-Full-of-Dreams.json"}, []string{}))
}

func assertDocumentServerError(t *testing.T, status int, errorMessage string) {
    client := &mockHttpClient{ nextStatus: status, nextBody: errorMessage, }
	assert.Equal(t,
	             "\x1b[31mError from container (document api) at 127.0.0.1:8080 (Status " + strconv.Itoa(status) + "):\n" + errorMessage + "\n",
	             executeCommand(t, client, []string{"document", "post",
	                                                "mynamespace/music/docid/1",
	                                                "testdata/A-Head-Full-of-Dreams.json"}, []string{}))
}