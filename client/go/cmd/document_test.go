// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// document command tests
// Author: bratseth

package cmd

import (
	"io/ioutil"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func TestDocumentSendPut(t *testing.T) {
	assertDocumentSend([]string{"document", "testdata/A-Head-Full-of-Dreams-Put.json"},
		"put", "POST", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Put.json", t)
}

func TestDocumentSendPutVerbose(t *testing.T) {
	assertDocumentSend([]string{"document", "-v", "testdata/A-Head-Full-of-Dreams-Put.json"},
		"put", "POST", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Put.json", t)
}

func TestDocumentSendUpdate(t *testing.T) {
	assertDocumentSend([]string{"document", "testdata/A-Head-Full-of-Dreams-Update.json"},
		"update", "PUT", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Update.json", t)
}

func TestDocumentSendRemove(t *testing.T) {
	assertDocumentSend([]string{"document", "testdata/A-Head-Full-of-Dreams-Remove.json"},
		"remove", "DELETE", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Remove.json", t)
}

func TestDocumentPutWithIdArg(t *testing.T) {
	assertDocumentSend([]string{"document", "put", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Without-Operation.json"},
		"put", "POST", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Without-Operation.json", t)
}

func TestDocumentPutWithoutIdArg(t *testing.T) {
	assertDocumentSend([]string{"document", "put", "testdata/A-Head-Full-of-Dreams-Put.json"},
		"put", "POST", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Put.json", t)
}

func TestDocumentUpdateWithIdArg(t *testing.T) {
	assertDocumentSend([]string{"document", "update", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Without-Operation.json"},
		"update", "PUT", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Without-Operation.json", t)
}

func TestDocumentUpdateWithoutIdArg(t *testing.T) {
	assertDocumentSend([]string{"document", "update", "testdata/A-Head-Full-of-Dreams-Update.json"},
		"update", "PUT", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Update.json", t)
}

func TestDocumentRemoveWithIdArg(t *testing.T) {
	assertDocumentSend([]string{"document", "remove", "id:mynamespace:music::a-head-full-of-dreams"},
		"remove", "DELETE", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Remove.json", t)
}

func TestDocumentRemoveWithoutIdArg(t *testing.T) {
	assertDocumentSend([]string{"document", "remove", "testdata/A-Head-Full-of-Dreams-Remove.json"},
		"remove", "DELETE", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Remove.json", t)
}

func TestDocumentSendMissingId(t *testing.T) {
	arguments := []string{"document", "put", "testdata/A-Head-Full-of-Dreams-Without-Operation.json"}
	client := &mockHttpClient{}
	_, outErr := execute(command{args: arguments}, t, client)
	assert.Equal(t,
		"Error: No document id given neither as argument or as a 'put' key in the json file\n",
		outErr)
}

func TestDocumentSendWithDisagreeingOperations(t *testing.T) {
	arguments := []string{"document", "update", "testdata/A-Head-Full-of-Dreams-Put.json"}
	client := &mockHttpClient{}
	_, outErr := execute(command{args: arguments}, t, client)
	assert.Equal(t,
		"Error: Wanted document operation is update but the JSON file specifies put\n",
		outErr)
}

func TestDocumentPutDocumentError(t *testing.T) {
	assertDocumentError(t, 401, "Document error")
}

func TestDocumentPutServerError(t *testing.T) {
	assertDocumentServerError(t, 501, "Server error")
}

func TestDocumentGet(t *testing.T) {
	assertDocumentGet([]string{"document", "get", "id:mynamespace:music::a-head-full-of-dreams"},
		"id:mynamespace:music::a-head-full-of-dreams", t)
}

func assertDocumentSend(arguments []string, expectedOperation string, expectedMethod string, expectedDocumentId string, expectedPayloadFile string, t *testing.T) {
	client := &mockHttpClient{}
	documentURL := documentServiceURL(client)
	expectedPath, _ := vespa.IdToURLPath(expectedDocumentId)
	expectedURL := documentURL + "/document/v1/" + expectedPath
	out, errOut := execute(command{args: arguments}, t, client)

	verbose := false
	for _, a := range arguments {
		if a == "-v" {
			verbose = true
		}
	}
	if verbose {
		expectedCurl := "curl -X " + expectedMethod + " -H 'Content-Type: application/json' --data-binary @" + expectedPayloadFile + " " + expectedURL + "\n"
		assert.Equal(t, expectedCurl, errOut)
	}
	assert.Equal(t, "Success: "+expectedOperation+" "+expectedDocumentId+"\n", out)
	assert.Equal(t, expectedURL, client.lastRequest.URL.String())
	assert.Equal(t, "application/json", client.lastRequest.Header.Get("Content-Type"))
	assert.Equal(t, expectedMethod, client.lastRequest.Method)

	expectedPayload, _ := ioutil.ReadFile(expectedPayloadFile)
	assert.Equal(t, string(expectedPayload), util.ReaderToString(client.lastRequest.Body))
}

func assertDocumentGet(arguments []string, documentId string, t *testing.T) {
	client := &mockHttpClient{}
	documentURL := documentServiceURL(client)
	client.NextResponse(200, "{\"fields\":{\"foo\":\"bar\"}}")
	assert.Equal(t,
		`{
    "fields": {
        "foo": "bar"
    }
}
`,
		executeCommand(t, client, arguments, []string{}))
	expectedPath, _ := vespa.IdToURLPath(documentId)
	assert.Equal(t, documentURL+"/document/v1/"+expectedPath, client.lastRequest.URL.String())
	assert.Equal(t, "GET", client.lastRequest.Method)
}

func assertDocumentError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{}
	client.NextResponse(status, errorMessage)
	_, outErr := execute(command{args: []string{"document", "put",
		"id:mynamespace:music::a-head-full-of-dreams",
		"testdata/A-Head-Full-of-Dreams-Put.json"}}, t, client)
	assert.Equal(t,
		"Error: Invalid document operation: Status "+strconv.Itoa(status)+"\n\n"+errorMessage+"\n",
		outErr)
}

func assertDocumentServerError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{}
	client.NextResponse(status, errorMessage)
	_, outErr := execute(command{args: []string{"document", "put",
		"id:mynamespace:music::a-head-full-of-dreams",
		"testdata/A-Head-Full-of-Dreams-Put.json"}}, t, client)
	assert.Equal(t,
		"Error: Container (document API) at 127.0.0.1:8080: Status "+strconv.Itoa(status)+"\n\n"+errorMessage+"\n",
		outErr)
}

func documentServiceURL(client *mockHttpClient) string {
	return getService("document", 0).BaseURL
}
