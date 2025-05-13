// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// test command tests
// Author: jonmv

package cmd

import (
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func TestSuite(t *testing.T) {
	client := &mock.HTTPClient{}
	searchResponse, _ := os.ReadFile("testdata/tests/response.json")
	mockServiceStatus(client, "container")
	client.NextStatus(200)
	client.NextStatus(200)
	for range 2 {
		client.NextResponseString(200, string(searchResponse))
	}
	mockServiceStatus(client, "container") // Some tests do not specify cluster, which is fine since we only have one, but this causes a cache miss
	for range 9 {
		client.NextResponseString(200, string(searchResponse))
	}
	expectedBytes, _ := os.ReadFile("testdata/tests/expected-suite.out")
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("test", "testdata/tests/system-test"))
	assert.Equal(t, "", stderr.String())
	baseUrl := "http://127.0.0.1:8080"
	urlWithQuery := baseUrl + "/search/?presentation.timing=true&query=artist%3A+foo&timeout=3.4s"
	discoveryRequest := createDiscoveryRequest()
	requests := []*http.Request{discoveryRequest, createFeedRequest(baseUrl), createFeedRequest(baseUrl), createSearchRequest(urlWithQuery), createRequestWithCustomHeader(urlWithQuery)}
	requests = append(requests, discoveryRequest)
	requests = append(requests, createSearchRequest(baseUrl+"/search/"))
	requests = append(requests, createSearchRequest(baseUrl+"/search/?foo=%2F"))
	for range 7 {
		requests = append(requests, createSearchRequest(baseUrl+"/search/"))
	}
	assertRequests(requests, client, t)
	assert.Equal(t, string(expectedBytes), stdout.String())
	assert.Equal(t, "", stderr.String())
}

func TestIllegalFileReference(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextStatus(200)
	client.NextStatus(200)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("test", "testdata/tests/production-test/illegal-reference.json"))
	assertRequests([]*http.Request{createRequest("GET", "https://domain.tld", "{}")}, client, t)
	assert.Equal(t, "\nError: error in Step 2: path may not point outside src/test/application, but 'foo/../../../../this-is-not-ok.json' does\nHint: See https://docs.vespa.ai/en/reference/testing\n", stderr.String())
}

func TestIllegalRequestUri(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextStatus(200)
	client.NextStatus(200)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("test", "testdata/tests/production-test/illegal-uri.json"))
	assertRequests([]*http.Request{createRequest("GET", "https://domain.tld/my-api", "")}, client, t)
	assert.Equal(t, "\nError: error in Step 2: production tests may not specify requests against Vespa endpoints\nHint: See https://docs.vespa.ai/en/reference/testing\n", stderr.String())
}

func TestProductionTest(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextStatus(200)
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.Nil(t, cli.Run("test", "testdata/tests/production-test/external.json"))
	assert.Equal(t, "external.json: . OK\n\nSuccess: 1 test OK\n", stdout.String())
	assert.Equal(t, "", stderr.String())
	assertRequests([]*http.Request{createRequest("GET", "https://my.service:123/path?query=wohoo", "")}, client, t)
}

func TestTestWithoutAssertions(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	assert.NotNil(t, cli.Run("test", "testdata/tests/system-test/foo/query.json"))
	assert.Equal(t, "\nError: a test must have at least one step, but none were found in testdata/tests/system-test/foo/query.json\nHint: See https://docs.vespa.ai/en/reference/testing\n", stderr.String())
}

func TestSuiteWithoutTests(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	assert.NotNil(t, cli.Run("test", "testdata/tests/staging-test"))
	assert.Equal(t, "Error: failed to find any tests at testdata/tests/staging-test\nHint: See https://docs.vespa.ai/en/reference/testing\n", stderr.String())
}

func TestSingleTest(t *testing.T) {
	client := &mock.HTTPClient{}
	searchResponse, _ := os.ReadFile("testdata/tests/response.json")
	mockServiceStatus(client, "container")
	client.NextStatus(200)
	client.NextStatus(200)
	client.NextResponseString(200, string(searchResponse))
	client.NextResponseString(200, string(searchResponse))
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	expectedBytes, _ := os.ReadFile("testdata/tests/expected.out")
	assert.Nil(t, cli.Run("test", "testdata/tests/system-test/test.json"))
	assert.Equal(t, string(expectedBytes), stdout.String())
	assert.Equal(t, "", stderr.String())

	baseUrl := "http://127.0.0.1:8080"
	rawUrl := baseUrl + "/search/?presentation.timing=true&query=artist%3A+foo&timeout=3.4s"
	discoveryRequest := createDiscoveryRequest()
	assertRequests([]*http.Request{discoveryRequest, createFeedRequest(baseUrl), createFeedRequest(baseUrl), createSearchRequest(rawUrl), createRequestWithCustomHeader(rawUrl)}, client, t)
}

func TestSingleTestWithCloudAndEndpoints(t *testing.T) {
	apiKey, err := vespa.CreateAPIKey()
	require.Nil(t, err)
	certDir := filepath.Join(t.TempDir())
	keyFile := filepath.Join(certDir, "key")
	certFile := filepath.Join(certDir, "cert")
	kp, err := vespa.CreateKeyPair()
	require.Nil(t, err)
	require.Nil(t, os.WriteFile(keyFile, kp.PrivateKey, 0600))
	require.Nil(t, os.WriteFile(certFile, kp.Certificate, 0600))

	client := &mock.HTTPClient{}
	cli, stdout, stderr := newTestCLI(
		t,
		"VESPA_CLI_API_KEY="+string(apiKey),
		"VESPA_CLI_DATA_PLANE_KEY_FILE="+keyFile,
		"VESPA_CLI_DATA_PLANE_CERT_FILE="+certFile,
		"VESPA_CLI_ENDPOINTS={\"endpoints\":[{\"cluster\":\"container\",\"url\":\"https://url\"}]}",
	)
	cli.httpClient = client

	searchResponse, err := os.ReadFile("testdata/tests/response.json")
	require.Nil(t, err)
	client.NextStatus(200)
	client.NextStatus(200)
	client.NextResponseString(200, string(searchResponse))
	client.NextResponseString(200, string(searchResponse))

	assert.Nil(t, cli.Run("test", "testdata/tests/system-test/test.json", "-t", "cloud", "-a", "t.a.i"))
	expectedBytes, err := os.ReadFile("testdata/tests/expected.out")
	require.Nil(t, err)
	assert.Equal(t, "", stderr.String())
	assert.Equal(t, string(expectedBytes), stdout.String())

	baseUrl := "https://url"
	rawUrl := baseUrl + "/search/?presentation.timing=true&query=artist%3A+foo&timeout=3.4s"
	assertRequests([]*http.Request{createFeedRequest(baseUrl), createFeedRequest(baseUrl), createSearchRequest(rawUrl), createRequestWithCustomHeader(rawUrl)}, client, t)
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
	r := &http.Request{
		URL:    requestUrl,
		Method: method,
		Header: http.Header{},
		Body:   io.NopCloser(strings.NewReader(body)),
	}
	r.Header.Set("Content-Type", "application/json")
	return r
}

func createRequestWithCustomHeader(url string) *http.Request {
	r := createSearchRequest(url)
	r.Header.Set("X-Foo", "bar")
	return r
}

func createDiscoveryRequest() *http.Request {
	r := createSearchRequest("http://127.0.0.1:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge")
	r.Header = http.Header{}
	return r
}

func assertRequests(requests []*http.Request, client *mock.HTTPClient, t *testing.T) {
	t.Helper()
	require.Equal(t, len(requests), len(client.Requests))
	for i, want := range requests {
		got := client.Requests[i]
		assert.Equal(t, want.URL.String(), got.URL.String())
		assert.Equal(t, want.Method, got.Method)
		assert.Equal(t, want.Header, got.Header)
		actualBody := got.Body
		if actualBody == nil {
			actualBody = io.NopCloser(strings.NewReader(""))
		}
		assert.Equal(t, ioutil.ReaderToJSON(want.Body), ioutil.ReaderToJSON(actualBody))
	}
}
