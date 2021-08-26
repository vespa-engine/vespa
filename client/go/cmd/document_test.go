// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// document command tests
// Author: bratseth

package cmd

import (
	"io/ioutil"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/util"
)

func TestDocumentPostWithIdArg(t *testing.T) {
	assertDocumentPost([]string{"document", "post", "mynamespace/music/docid/1", "testdata/A-Head-Full-of-Dreams.json"},
		"mynamespace/music/docid/1", "testdata/A-Head-Full-of-Dreams.json", t)
}

func TestDocumentPostWithIdInDocument(t *testing.T) {
	assertDocumentPost([]string{"document", "post", "testdata/A-Head-Full-of-Dreams-With-Id.json"},
		"mynamespace/music/docid/1", "testdata/A-Head-Full-of-Dreams-With-Id.json", t)
}

func TestDocumentPostWithIdInDocumentShortForm(t *testing.T) {
	assertDocumentPost([]string{"document", "testdata/A-Head-Full-of-Dreams-With-Id.json"},
		"mynamespace/music/docid/1", "testdata/A-Head-Full-of-Dreams-With-Id.json", t)
}

func TestDocumentPostWithIdAsPutInDocument(t *testing.T) {
	assertDocumentPost([]string{"document", "post", "testdata/A-Head-Full-of-Dreams-With-Put.json"},
		"mynamespace/music/docid/1", "testdata/A-Head-Full-of-Dreams-With-Put.json", t)
}

func TestDocumentIdNotSpecified(t *testing.T) {
	arguments := []string{"document", "post", "testdata/A-Head-Full-of-Dreams.json"}
	client := &mockHttpClient{}
	assert.Equal(t,
		"No document id given neither as argument or an 'id' key in the json file\n",
		executeCommand(t, client, arguments, []string{}))
}

func TestDocumentPostDocumentError(t *testing.T) {
	assertDocumentError(t, 401, "Document error")
}

func TestDocumentPostServerError(t *testing.T) {
	assertDocumentServerError(t, 501, "Server error")
}

func assertDocumentPost(arguments []string, documentId string, jsonFile string, t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		documentId+"\n",
		executeCommand(t, client, arguments, []string{}))
	target := getTarget(documentContext).document
	assert.Equal(t, target+"/document/v1/"+documentId, client.lastRequest.URL.String())
	assert.Equal(t, "application/json", client.lastRequest.Header.Get("Content-Type"))
	assert.Equal(t, "POST", client.lastRequest.Method)

	fileContent, _ := ioutil.ReadFile(jsonFile)
	assert.Equal(t, string(fileContent), util.ReaderToString(client.lastRequest.Body))
}

func assertDocumentPostShortForm(documentId string, jsonFile string, t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"Success\n",
		executeCommand(t, client, []string{"document", jsonFile}, []string{}))
	target := getTarget(documentContext).document
	assert.Equal(t, target+"/document/v1/"+documentId, client.lastRequest.URL.String())
}

func assertDocumentError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{nextStatus: status, nextBody: errorMessage}
	assert.Equal(t,
		"Invalid document (Status "+strconv.Itoa(status)+"):\n"+errorMessage+"\n",
		executeCommand(t, client, []string{"document", "post",
			"mynamespace/music/docid/1",
			"testdata/A-Head-Full-of-Dreams.json"}, []string{}))
}

func assertDocumentServerError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{nextStatus: status, nextBody: errorMessage}
	assert.Equal(t,
		"Error from container (document api) at 127.0.0.1:8080 (Status "+strconv.Itoa(status)+"):\n"+errorMessage+"\n",
		executeCommand(t, client, []string{"document", "post",
			"mynamespace/music/docid/1",
			"testdata/A-Head-Full-of-Dreams.json"}, []string{}))
}
