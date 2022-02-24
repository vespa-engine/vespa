// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// test command tests
// Author: jonmv

package cmd

import (
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func TestSuite(t *testing.T) {
	client := &mockHttpClient{}
	searchResponse, _ := ioutil.ReadFile("testdata/tests/response.json")
	client.NextStatus(200)
	client.NextStatus(200)
	for i := 0; i < 11; i++ {
		client.NextResponse(200, string(searchResponse))
	}

	expectedBytes, _ := ioutil.ReadFile("testdata/tests/expected-suite.out")
	outBytes, errBytes := execute(command{args: []string{"test", "testdata/tests/system-test"}}, t, client)

	baseUrl := "http://127.0.0.1:8080"
	urlWithQuery := baseUrl + "/search/?presentation.timing=true&query=artist%3A+foo&timeout=3.4s"
	requests := []*http.Request{createFeedRequest(baseUrl), createFeedRequest(baseUrl), createSearchRequest(urlWithQuery), createSearchRequest(urlWithQuery)}
	requests = append(requests, createSearchRequest(baseUrl+"/search/"))
	requests = append(requests, createSearchRequest(baseUrl+"/search/?foo=%2F"))
	for i := 0; i < 7; i++ {
		requests = append(requests, createSearchRequest(baseUrl+"/search/"))
	}
	assertRequests(requests, client, t)
	assert.Equal(t, string(expectedBytes), outBytes)
	assert.Equal(t, "", errBytes)
}

func TestIllegalFileReference(t *testing.T) {
	client := &mockHttpClient{}
	client.NextStatus(200)
	client.NextStatus(200)
	_, errBytes := execute(command{args: []string{"test", "testdata/tests/production-test/illegal-reference.json"}}, t, client)
	assertRequests([]*http.Request{createRequest("GET", "http://127.0.0.1:8080/search/", "{}")}, client, t)
	assert.Equal(t, "\nError: error in Step 2: path may not point outside src/test/application, but 'foo/../../../../this-is-not-ok.json' does\nHint: See https://cloud.vespa.ai/en/reference/testing\n", errBytes)
}

func TestProductionTest(t *testing.T) {
	client := &mockHttpClient{}
	client.NextStatus(200)
	outBytes, errBytes := execute(command{args: []string{"test", "testdata/tests/production-test/external.json"}}, t, client)
	assert.Equal(t, "external.json: . OK\n\nSuccess: 1 test OK\n", outBytes)
	assert.Equal(t, "", errBytes)
	assertRequests([]*http.Request{createRequest("GET", "https://my.service:123/path?query=wohoo", "")}, client, t)
}

func TestTestWithoutAssertions(t *testing.T) {
	client := &mockHttpClient{}
	_, errBytes := execute(command{args: []string{"test", "testdata/tests/system-test/foo/query.json"}}, t, client)
	assert.Equal(t, "\nError: a test must have at least one step, but none were found in testdata/tests/system-test/foo/query.json\nHint: See https://cloud.vespa.ai/en/reference/testing\n", errBytes)
}

func TestSuiteWithoutTests(t *testing.T) {
	client := &mockHttpClient{}
	_, errBytes := execute(command{args: []string{"test", "testdata/tests/staging-test"}}, t, client)
	assert.Equal(t, "Error: failed to find any tests at testdata/tests/staging-test\nHint: See https://cloud.vespa.ai/en/reference/testing\n", errBytes)
}

func TestSingleTest(t *testing.T) {
	client := &mockHttpClient{}
	searchResponse, _ := ioutil.ReadFile("testdata/tests/response.json")
	client.NextStatus(200)
	client.NextStatus(200)
	client.NextResponse(200, string(searchResponse))
	client.NextResponse(200, string(searchResponse))

	expectedBytes, _ := ioutil.ReadFile("testdata/tests/expected.out")
	outBytes, errBytes := execute(command{args: []string{"test", "testdata/tests/system-test/test.json"}}, t, client)
	assert.Equal(t, string(expectedBytes), outBytes)
	assert.Equal(t, "", errBytes)

	baseUrl := "http://127.0.0.1:8080"
	rawUrl := baseUrl + "/search/?presentation.timing=true&query=artist%3A+foo&timeout=3.4s"
	assertRequests([]*http.Request{createFeedRequest(baseUrl), createFeedRequest(baseUrl), createSearchRequest(rawUrl), createSearchRequest(rawUrl)}, client, t)
}

func TestSingleTestWithCloudAndEndpoints(t *testing.T) {
	apiKey, err := vespa.CreateAPIKey()
	require.Nil(t, err)
	cmd := command{
		args: []string{"test", "testdata/tests/system-test/test.json", "-t", "cloud", "-a", "t.a.i"},
		env:  map[string]string{"VESPA_CLI_API_KEY": string(apiKey)},
	}
	cmd.homeDir = filepath.Join(t.TempDir(), ".vespa")
	os.MkdirAll(cmd.homeDir, 0700)
	keyFile := filepath.Join(cmd.homeDir, "key")
	certFile := filepath.Join(cmd.homeDir, "cert")

	os.Setenv("VESPA_CLI_DATA_PLANE_KEY_FILE", keyFile)
	os.Setenv("VESPA_CLI_DATA_PLANE_CERT_FILE", certFile)
	os.Setenv("VESPA_CLI_ENDPOINTS", "{\"endpoints\":[{\"cluster\":\"container\",\"url\":\"https://url\"}]}")

	kp, _ := vespa.CreateKeyPair()
	ioutil.WriteFile(keyFile, kp.PrivateKey, 0600)
	ioutil.WriteFile(certFile, kp.Certificate, 0600)

	client := &mockHttpClient{}
	searchResponse, _ := ioutil.ReadFile("testdata/tests/response.json")
	client.NextStatus(200)
	client.NextStatus(200)
	client.NextResponse(200, string(searchResponse))
	client.NextResponse(200, string(searchResponse))

	expectedBytes, _ := ioutil.ReadFile("testdata/tests/expected.out")
	outBytes, errBytes := execute(cmd, t, client)
	assert.Equal(t, string(expectedBytes), outBytes)
	assert.Equal(t, "", errBytes)

	baseUrl := "https://url"
	rawUrl := baseUrl + "/search/?presentation.timing=true&query=artist%3A+foo&timeout=3.4s"
	assertRequests([]*http.Request{createFeedRequest(baseUrl), createFeedRequest(baseUrl), createSearchRequest(rawUrl), createSearchRequest(rawUrl)}, client, t)
}

func createFeedRequest(urlPrefix string) *http.Request {
	return createRequest("POST",
		urlPrefix+"/document/v1/test/music/docid/doc?timeout=3.4s",
		"{\"fields\":{\"artist\":\"Foo Fighters\"}}")
}

func createSearchRequest(rawUrl string) *http.Request {
	return createRequest("GET", rawUrl, "")
}

func createRequest(method string, uri string, body string) *http.Request {
	requestUrl, _ := url.ParseRequestURI(uri)
	return &http.Request{
		URL:    requestUrl,
		Method: method,
		Header: nil,
		Body:   ioutil.NopCloser(strings.NewReader(body)),
	}
}

func assertRequests(requests []*http.Request, client *mockHttpClient, t *testing.T) {
	if assert.Equal(t, len(requests), len(client.requests)) {
		for i, e := range requests {
			a := client.requests[i]
			assert.Equal(t, e.URL.String(), a.URL.String())
			assert.Equal(t, e.Method, a.Method)
			assert.Equal(t, util.ReaderToJSON(e.Body), util.ReaderToJSON(a.Body))
		}
	}
}
