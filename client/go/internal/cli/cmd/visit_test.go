// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	normalpre = `{"pathId":"/document/v1/","documents":[`
	document1 = `{"id":"id:t:m::1","fields":{"title":"t"}}`
	document2 = `{"id":"id:t:m::2","fields":{"title":"t2"}}`
	document3 = `{"id":"id:t:m::3","fields":{"ar":"xyz","w":63,"title":"xyzzy","year":2000}}`

	savedresponse = `{"pathId":"/document/v1/","documents":[{"id":"id:test:music::1921492307","fields":{"title":"song","year":2010}},{"id":"id:test:music::p_try-this-clean-bonus-dvd-_music_1922003403","fields":{"artist":"xyz","weight":600000,"song":"hate","title":"xyz","year":2000}}],"documentCount":2,"continuation":"AAAACAAAAAAAAAAJAAAAAAAAAAgAAAAAAAABAAAAAAEgAAAAAAAAEAAAAAAAAAAA"}`

	saveddoc0        = `{"id":"id:test:music::1921492307","fields":{"title":"song","year":2010}}`
	saveddoc1        = `{"id":"id:test:music::p_try-this-clean-bonus-dvd-_music_1922003403","fields":{"artist":"xyz","weight":600000,"song":"hate","title":"xyz","year":2000}}`
	handlersResponse = `{
  "handlers" : [ {
    "id" : "com.yahoo.container.usability.BindingsOverviewHandler",
    "class" : "com.yahoo.container.usability.BindingsOverviewHandler",
    "bundle" : "container-disc:8.0.0",
    "serverBindings" : [ "http://*/" ]
  }, {
    "id" : "com.yahoo.document.restapi.resource.DocumentV1ApiHandler",
    "class" : "com.yahoo.document.restapi.resource.DocumentV1ApiHandler",
    "bundle" : "vespaclient-container-plugin:8.0.0",
    "serverBindings" : [ "http://*/document/v1/*", "http://*/document/v1/*/" ]
  } ]
}`
	clusterStarResponse = `{"pathId":"/document/v1/","message":"Your Vespa deployment has no content cluster '*', only 'fooCC'"}`
	overloadResponse    = `{"pathId":"/document/v1/","message":"Rejecting execution due to overload: 12345 requests already enqueued"}`
)

func TestQuoteFunc(t *testing.T) {
	var buf []byte = make([]byte, 3)
	buf[0] = 'a'
	buf[2] = 'z'
	for i := range 256 {
		buf[1] = byte(i)
		s := string(buf)
		res := quoteArgForUrl(s)
		if i < 32 || i > 127 {
			assert.Equal(t, "a+z", res)
		} else if testing.Verbose() { // go test -v
			fmt.Printf("res %3d => '%s'\n", i, res)
		}
	}
}

// low-level (unit) test
func TestRunOneVisit(t *testing.T) {
	withResponse := func(client *mock.HTTPClient) {
		client.NextResponseString(200, savedresponse)
	}
	op := func(service *vespa.Service) {
		vArgs := visitArgs{
			contentCluster: "fooCC",
			header:         make(http.Header),
			stream:         true,
		}
		vArgs.header.Set("X-Foo", "Bar")
		vvo, res := runOneVisit(&vArgs, service, "BBBB")
		assert.Equal(t, true, res.Success)
		assert.Equal(t, "visited fooCC", res.Message)
		assert.Equal(t, "/document/v1/", vvo.PathId)
		assert.Equal(t, "", vvo.ErrorMsg)
		assert.Equal(t, "AAAACAAAAAAAAAAJAAAAAAAAAAgAAAAAAAABAAAAAAEgAAAAAAAAEAAAAAAAAAAA", vvo.Continuation)
		assert.Equal(t, 2, vvo.DocumentCount)
		assert.Equal(t, 2, len(vvo.Documents))
		assert.Equal(t, saveddoc0, string(vvo.Documents[0].blob))
		assert.Equal(t, saveddoc1, string(vvo.Documents[1].blob))
	}
	req := withMockClient(t, withResponse, op)
	assert.Equal(t, "cluster=fooCC&continuation=BBBB&stream=true", req.URL.RawQuery)
	assert.Equal(t, "Bar", req.Header.Get("X-Foo"))

	op = func(service *vespa.Service) {
		vArgs := visitArgs{
			contentCluster: "search",
			fieldSet:       "[id]",
			selection:      "music.year>2000",
			chunkCount:     123,
		}
		vvo, res := runOneVisit(&vArgs, service, "asdf")
		assert.Equal(t, true, res.Success)
		assert.Equal(t, 2, vvo.DocumentCount)
	}
	req = withMockClient(t, withResponse, op)
	assert.Equal(t, "cluster=search&fieldSet=%5Bid%5D&selection=music%2Eyear%3E2000&continuation=asdf&wantedDocumentCount=123&stream=false", req.URL.RawQuery)
}

func withMockClient(t *testing.T, prepCli func(*mock.HTTPClient), runOp func(*vespa.Service)) *http.Request {
	client := &mock.HTTPClient{}
	mockServiceStatus(client, "container")
	prepCli(client)
	cli, _, _ := newTestCLI(t)
	cli.httpClient = client
	service, err := documentService(cli, &Waiter{cli: cli})
	if err != nil {
		t.Fatal(err)
	}
	runOp(service)
	return client.LastRequest
}

type responseCodeAndPayload struct {
	httpCode int
	payload  string
}

func TestVisitCommand(t *testing.T) {
	assertVisitResults(
		[]string{
			"visit",
			"--bucket-space", "default",
			"--json-lines",
		},
		t,
		[]responseCodeAndPayload{
			{200, handlersResponse},
			{400, clusterStarResponse},
			// 429s shall be transparently retried with random exponential backoff
			{429, overloadResponse},
			{429, overloadResponse},
			{429, overloadResponse},
			{200, normalpre +
				document1 +
				`],"documentCount":1,"continuation":"CAFE"}`},
			// Continuation token must be retained across 429 retries
			{429, overloadResponse},
			{200, normalpre +
				document2 +
				"," +
				document3 +
				`],"documentCount":2}`},
		},
		"cluster=fooCC&continuation=CAFE&wantedDocumentCount=1000&bucketSpace=default&stream=false",
		document1+"\n"+
			document2+"\n"+
			document3+"\n")
}

const (
	jsonlDoc1      = `{"put":"id:space:music::1","fields":{"title":"first"}}`
	jsonlDoc2      = `{"put":"id:space:music::2","fields":{"title":"second"}}`
	jsonlRemoveDoc = `{"remove":"id:space:music::3","fields":{"title":"third"}}`
	jsonlCont      = `{"continuation":{"token":"JSONL_TOKEN","percentFinished":50.0}}`
	jsonlDone      = `{"continuation":{"percentFinished":100.0}}`
	jsonlStats     = `{"sessionStats":{"documentCount":2}}`
	jsonlOutDoc1   = `{"id":"id:space:music::1","fields":{"title":"first"}}`
	jsonlOutDoc2   = `{"id":"id:space:music::2","fields":{"title":"second"}}`
)

func jsonlResponse(body string) mock.HTTPResponse {
	return mock.HTTPResponse{
		Status: 200,
		Body:   []byte(body),
		Header: http.Header{"Content-Type": []string{"application/jsonl; charset=UTF-8"}},
	}
}

func TestRunOneVisitJSONL(t *testing.T) {
	// Complete response: docs + stats + done signal
	body := jsonlDoc1 + "\n" + jsonlDoc2 + "\n" + jsonlStats + "\n" + jsonlDone + "\n"
	client := &mock.HTTPClient{}
	mockServiceStatus(client, "container")
	client.NextResponse(jsonlResponse(body))
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	service, err := documentService(cli, &Waiter{cli: cli})
	assert.Nil(t, err)
	vArgs := visitArgs{
		contentCluster: "fooCC",
		stream:         true,
		jsonLines:      true,
		chunkCount:     1000,
		header:         make(http.Header),
		cli:            cli,
	}
	vvo, res := runOneVisit(&vArgs, service, "")
	assert.True(t, res.Success)
	assert.Equal(t, "application/json, application/jsonl", client.LastRequest.Header.Get("Accept"))
	assert.Equal(t, "", vvo.Continuation)
	assert.False(t, vvo.Truncated)
	assert.Equal(t, 2, vvo.DocumentCount)
	assert.Equal(t, jsonlOutDoc1+"\n"+jsonlOutDoc2+"\n", stdout.String())
}

func TestVisitCommandJSONL(t *testing.T) {
	// JSONL is a single streaming response — all docs in one body with done signal
	body := jsonlDoc1 + "\n" + jsonlDoc2 + "\n" + jsonlDone + "\n"
	client := &mock.HTTPClient{}
	client.NextResponseString(200, handlersResponse)
	client.NextResponse(jsonlResponse(body))
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client
	cli.sleeper = func(d time.Duration) {}
	assert.Nil(t, cli.Run("visit", "--stream", "--json-lines", "--bucket-space", "default", "--content-cluster", "fooCC", "-t", "http://127.0.0.1:8080"))
	assert.Equal(t, jsonlOutDoc1+"\n"+jsonlOutDoc2+"\n", stdout.String())
	assert.Equal(t, "", stderr.String())
	assert.Equal(t, 2, len(client.Requests))
	assert.Equal(t, "application/json, application/jsonl", client.LastRequest.Header.Get("Accept"))
	assert.Equal(t, "cluster=fooCC&wantedDocumentCount=1000&bucketSpace=default&stream=true", client.LastRequest.URL.RawQuery)
}

func TestRunOneVisitJSONLUserAcceptHeader(t *testing.T) {
	body := jsonlDoc1 + "\n" + jsonlStats + "\n" + jsonlDone + "\n"
	client := &mock.HTTPClient{}
	mockServiceStatus(client, "container")
	client.NextResponse(jsonlResponse(body))
	cli, _, _ := newTestCLI(t)
	cli.httpClient = client
	service, err := documentService(cli, &Waiter{cli: cli})
	assert.Nil(t, err)
	userHeader := make(http.Header)
	userHeader.Set("Accept", "application/json")
	vArgs := visitArgs{
		contentCluster: "fooCC",
		stream:         true,
		jsonLines:      true,
		chunkCount:     1000,
		header:         userHeader,
		cli:            cli,
	}
	_, res := runOneVisit(&vArgs, service, "")
	assert.True(t, res.Success)
	assert.Equal(t, "application/json", client.LastRequest.Header.Get("Accept"))
}

func TestVisitCommandJSONLTruncatedRetry(t *testing.T) {
	// First response has no trailing newline — truncated
	truncatedBody := jsonlDoc1 + "\n" + jsonlCont
	completeBody := jsonlDoc2 + "\n" + jsonlDone + "\n"
	client := &mock.HTTPClient{}
	client.NextResponseString(200, handlersResponse)
	client.NextResponse(jsonlResponse(truncatedBody))
	client.NextResponse(jsonlResponse(completeBody))
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client
	cli.sleeper = func(d time.Duration) {}
	assert.Nil(t, cli.Run("visit", "--stream", "--json-lines", "--bucket-space", "default", "--content-cluster", "fooCC", "-t", "http://127.0.0.1:8080"))
	assert.Contains(t, stdout.String(), jsonlOutDoc1+"\n")
	assert.Contains(t, stdout.String(), jsonlOutDoc2+"\n")
	assert.Contains(t, stderr.String(), "truncated")
	// Retry request must use the continuation token extracted from the truncated response
	assert.Equal(t, 3, len(client.Requests))
	assert.Contains(t, client.Requests[2].URL.RawQuery, "continuation=JSONL_TOKEN")
}

func TestVisitCommandJSONLTruncatedAbort(t *testing.T) {
	// Truncated 5 times in a row with no continuation token — should abort
	truncatedNoToken := jsonlDoc1 + "\n" // no continuation line, no done signal
	client := &mock.HTTPClient{}
	client.NextResponseString(200, handlersResponse)
	for range 5 {
		client.NextResponse(jsonlResponse(truncatedNoToken))
	}
	cli, _, _ := newTestCLI(t)
	cli.httpClient = client
	cli.sleeper = func(d time.Duration) {}
	err := cli.Run("visit", "--stream", "--json-lines", "--bucket-space", "default", "--content-cluster", "fooCC", "-t", "http://127.0.0.1:8080")
	assert.NotNil(t, err)
	assert.Contains(t, err.Error(), "truncated")
	assert.Equal(t, 6, len(client.Requests)) // probe + 5 visit attempts
}

func TestTruncatedMidJSONLMaxRetries(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, handlersResponse)
	// Testing trunctaion with different substrings of document from idx = 1 to idx = 11
	for i := range 5 {
		truncation_idx := (i * 5) + 1
		client.NextResponse(jsonlResponse(jsonlDoc1[0:truncation_idx]))
	}
	cli, _, _ := newTestCLI(t)
	cli.httpClient = client
	cli.sleeper = func(d time.Duration) {}
	err := cli.Run("visit", "--stream", "--json-lines", "--bucket-space", "default", "--content-cluster", "fooCC", "-t", "http://127.0.0.1:8080")
	assert.NotNil(t, err)
	assert.Contains(t, err.Error(), "truncated")
	assert.Equal(t, 6, len(client.Requests)) // probe + 5 visit attempts until it does not try retry any more
}

// Get truncated response 2 times and then the full response
func TestTruncatedMidJSONLSomeRetries(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, handlersResponse)
	// Testing trunctaion with different substrings of document from idx = 1 to idx = 11
	for i := range 2 {
		truncation_idx := (i * 5) + 1
		client.NextResponse(jsonlResponse(jsonlDoc1[0:truncation_idx]))
	}
	jsonlComplete := jsonlDoc1 + "\n" + jsonlDone
	client.NextResponse(jsonlResponse(jsonlComplete))
	cli, _, _ := newTestCLI(t)
	cli.httpClient = client
	cli.sleeper = func(d time.Duration) {}
	err := cli.Run("visit", "--stream", "--json-lines", "--bucket-space", "default", "--content-cluster", "fooCC", "-t", "http://127.0.0.1:8080")
	assert.Nil(t, err)
	assert.Equal(t, 4, len(client.Requests)) // probe + 3 visit attempts where the last one is a valid response
}

// All remove entreis should be ignored
func TestRemoveIgnored(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, handlersResponse)
	// Testing trunctaion with different substrings of document from idx = 1 to idx = 11
	response_doc := jsonlDoc1 + "\n" + jsonlDoc2 + "\n" + jsonlRemoveDoc + "\n" + jsonlDone + "\n"
	client.NextResponse(jsonlResponse(response_doc))
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client
	cli.sleeper = func(d time.Duration) {}
	err := cli.Run("visit", "--stream", "--json-lines", "--bucket-space", "default", "--content-cluster", "fooCC", "-t", "http://127.0.0.1:8080")
	assert.Nil(t, err)
	// The remove content is ignored
	assert.NotContains(t, stdout.String(), "third")
	assert.NotContains(t, stdout.String(), "3")
	assert.Equal(t, 2, len(client.Requests)) // probe + 1 visit
}

func inRangeMillis(v time.Duration, lo int64, hi int64) bool {
	return v.Milliseconds() >= lo && v.Milliseconds() <= hi
}

func assertVisitResults(arguments []string, t *testing.T, responses []responseCodeAndPayload, queryPart, output string) {
	t.Helper()
	client := &mock.HTTPClient{}
	for _, resp := range responses {
		client.NextResponseString(resp.httpCode, resp.payload)
	}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client
	var backoffs []time.Duration
	cli.sleeper = func(d time.Duration) { backoffs = append(backoffs, d) }
	arguments = append(arguments, "-t", "http://127.0.0.1:8080")
	assert.Nil(t, cli.Run(arguments...))
	assert.Equal(t, output, stdout.String())
	assert.Equal(t, "", stderr.String())
	assert.Equal(t, queryPart, client.LastRequest.URL.RawQuery)
	assert.Equal(t, "/document/v1/", client.LastRequest.URL.Path)
	assert.Equal(t, "GET", client.LastRequest.Method)

	assert.Equal(t, len(backoffs), 4)
	assert.True(t, inRangeMillis(backoffs[0], 100, 300)) // 200ms +- 100ms
	assert.True(t, inRangeMillis(backoffs[1], 150, 450)) // 300ms +- 150ms
	assert.True(t, inRangeMillis(backoffs[2], 225, 675)) // 450ms +- 225ms
	assert.True(t, inRangeMillis(backoffs[3], 100, 300)) // reset back to 200ms +- 100ms
}
