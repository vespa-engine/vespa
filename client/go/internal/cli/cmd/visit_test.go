// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"net/http"
	"testing"

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
)

func TestQuoteFunc(t *testing.T) {
	var buf []byte = make([]byte, 3)
	buf[0] = 'a'
	buf[2] = 'z'
	for i := 0; i < 256; i++ {
		buf[1] = byte(i)
		s := string(buf)
		res := quoteArgForUrl(s)
		if i < 32 || i > 127 {
			assert.Equal(t, "a+z", res)
		} else {
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
		}
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
	assert.Equal(t, "cluster=fooCC&continuation=BBBB", req.URL.RawQuery)

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
	assert.Equal(t, "cluster=search&fieldSet=%5Bid%5D&selection=music%2Eyear%3E2000&continuation=asdf&wantedDocumentCount=123", req.URL.RawQuery)
}

func withMockClient(t *testing.T, prepCli func(*mock.HTTPClient), runOp func(*vespa.Service)) *http.Request {
	client := &mock.HTTPClient{}
	prepCli(client)
	cli, _, _ := newTestCLI(t)
	cli.httpClient = client
	service, _ := documentService(cli)
	runOp(service)
	return client.LastRequest
}

func TestVisitCommand(t *testing.T) {
	assertVisitResults(
		[]string{
			"visit",
			"--json-lines",
		},
		t,
		[]string{
			normalpre +
				document1 +
				`],"documentCount":1,"continuation":"CAFE"}`,
			normalpre +
				document2 +
				"," +
				document3 +
				`],"documentCount":2}`,
		},
		"cluster=fooCC&continuation=CAFE&wantedDocumentCount=1000",
		document1+"\n"+
			document2+"\n"+
			document3+"\n")
}

func assertVisitResults(arguments []string, t *testing.T, responses []string, queryPart, output string) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, handlersResponse)
	client.NextResponseString(400, clusterStarResponse)
	for _, resp := range responses {
		client.NextResponseString(200, resp)
	}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.Nil(t, cli.Run(arguments...))
	assert.Equal(t, output, stdout.String())
	assert.Equal(t, "", stderr.String())
	assert.Equal(t, queryPart, client.LastRequest.URL.RawQuery)
	assert.Equal(t, "/document/v1/", client.LastRequest.URL.Path)
	assert.Equal(t, "GET", client.LastRequest.Method)
}
