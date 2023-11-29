// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// document command tests
// Author: bratseth

package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func TestDocumentSendPut(t *testing.T) {
	assertDocumentSend([]string{"document", "testdata/A-Head-Full-of-Dreams-Put.json"},
		"put", "POST", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Put.json", t)
}

func TestDocumentSendPutWithIdInFile(t *testing.T) {
	assertDocumentSend([]string{"document", "testdata/A-Head-Full-of-Dreams-Put-Id.json"},
		"put", "POST", "id:mynamespace:music::a-head-full-of-dreams", "testdata/A-Head-Full-of-Dreams-Put-Id.json", t)
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
		"remove", "DELETE", "id:mynamespace:music::a-head-full-of-dreams", "", t)
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
		"remove", "DELETE", "id:mynamespace:music::a-head-full-of-dreams", "", t)
}

func TestDocumentRemoveWithoutIdArg(t *testing.T) {
	assertDocumentSend([]string{"document", "remove", "testdata/A-Head-Full-of-Dreams-Remove.json"},
		"remove", "DELETE", "id:mynamespace:music::a-head-full-of-dreams", "", t)
}

func TestDocumentRemoveWithoutIdArgVerbose(t *testing.T) {
	assertDocumentSend([]string{"document", "remove", "-v", "testdata/A-Head-Full-of-Dreams-Remove.json"},
		"remove", "DELETE", "id:mynamespace:music::a-head-full-of-dreams", "", t)
}

func TestDocumentSendMissingId(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "document", "put", "testdata/A-Head-Full-of-Dreams-Without-Operation.json"))
	assert.Equal(t,
		"Error: no document id given neither as argument or as a 'put', 'update' or 'remove' key in the JSON file\n",
		stderr.String())
}

func TestDocumentSendWithDisagreeingOperations(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "document", "update", "testdata/A-Head-Full-of-Dreams-Put.json"))
	assert.Equal(t,
		"Error: wanted document operation is update, but JSON file specifies put\n",
		stderr.String())
}

func TestDocumentPutDocumentError(t *testing.T) {
	assertDocumentError(t, 401, "Document error")
}

func TestDocumentPutServerError(t *testing.T) {
	assertDocumentServerError(t, 501, "Server error")
}

func TestDocumentPutTransportError(t *testing.T) {
	assertDocumentTransportError(t, "Transport error")
}

func TestDocumentGet(t *testing.T) {
	assertDocumentGet([]string{"document", "get", "id:mynamespace:music::a-head-full-of-dreams"},
		"id:mynamespace:music::a-head-full-of-dreams", t)
}

func assertDocumentSend(args []string, expectedOperation string, expectedMethod string, expectedDocumentId string, expectedPayloadFile string, t *testing.T) {
	t.Helper()
	client := &mock.HTTPClient{}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client
	documentURL := "http://127.0.0.1:8080"
	expectedPath, _ := vespa.IdToURLPath(expectedDocumentId)
	expectedURL := documentURL + "/document/v1/" + expectedPath + "?timeout=60000ms"

	finalArgs := []string{"-t", documentURL}
	finalArgs = append(finalArgs, args...)
	assert.Nil(t, cli.Run(finalArgs...))
	verbose := false
	for _, a := range args {
		if a == "-v" {
			verbose = true
		}
	}
	if verbose {
		expectedCurl := "curl -X " + expectedMethod + " -m 66 -H 'Content-Type: application/json; charset=utf-8'"
		if expectedPayloadFile != "" {
			expectedCurl += " --data-binary @" + expectedPayloadFile
		}
		expectedCurl += " '" + expectedURL + "'\n"
		assert.Equal(t, expectedCurl, stderr.String())
	}
	assert.Equal(t, "Success: "+expectedOperation+" "+expectedDocumentId+"\n", stdout.String())
	assert.Equal(t, expectedURL, client.LastRequest.URL.String())
	assert.Equal(t, "application/json; charset=utf-8", client.LastRequest.Header.Get("Content-Type"))
	assert.Equal(t, expectedMethod, client.LastRequest.Method)

	if expectedPayloadFile != "" {
		data, err := os.ReadFile(expectedPayloadFile)
		assert.Nil(t, err)
		var expectedPayload struct {
			Fields json.RawMessage `json:"fields"`
		}
		assert.Nil(t, json.Unmarshal(data, &expectedPayload))
		assert.Equal(t, `{"fields":`+string(expectedPayload.Fields)+"}", ioutil.ReaderToString(client.LastRequest.Body))
	} else {
		assert.Nil(t, client.LastRequest.Body)
	}
}

func assertDocumentGet(args []string, documentId string, t *testing.T) {
	client := &mock.HTTPClient{}
	documentURL := "http://127.0.0.1:8080"
	client.NextResponseString(200, "{\"fields\":{\"foo\":\"bar\"}}")
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	finalArgs := []string{"-t", documentURL}
	finalArgs = append(finalArgs, args...)
	assert.Nil(t, cli.Run(finalArgs...))
	assert.Equal(t,
		`{
    "fields": {
        "foo": "bar"
    }
}
`,
		stdout.String())
	expectedPath, _ := vespa.IdToURLPath(documentId)
	assert.Equal(t, documentURL+"/document/v1/"+expectedPath, client.LastRequest.URL.String())
	assert.Equal(t, "GET", client.LastRequest.Method)
}

func assertDocumentTransportError(t *testing.T, errorMessage string) {
	client := &mock.HTTPClient{}
	client.NextResponseError(fmt.Errorf(errorMessage))
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "document", "put",
		"id:mynamespace:music::a-head-full-of-dreams",
		"testdata/A-Head-Full-of-Dreams-Put.json"))
	assert.Equal(t,
		"Error: "+errorMessage+"\n",
		stderr.String())
}

func assertDocumentError(t *testing.T, status int, errorMessage string) {
	client := &mock.HTTPClient{}
	client.NextResponseString(status, errorMessage)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "document", "put",
		"id:mynamespace:music::a-head-full-of-dreams",
		"testdata/A-Head-Full-of-Dreams-Put.json"))
	assert.Equal(t,
		"Error: Invalid document operation: Status "+strconv.Itoa(status)+"\n\n"+errorMessage+"\n",
		stderr.String())
}

func assertDocumentServerError(t *testing.T, status int, errorMessage string) {
	client := &mock.HTTPClient{}
	client.NextResponseString(status, errorMessage)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "document", "put",
		"id:mynamespace:music::a-head-full-of-dreams",
		"testdata/A-Head-Full-of-Dreams-Put.json"))
	assert.Equal(t,
		"Error: container at http://127.0.0.1:8080: Status "+strconv.Itoa(status)+"\n\n"+errorMessage+"\n",
		stderr.String())
}
