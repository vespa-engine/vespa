// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// query command tests
// Author: bratseth

package cmd

import (
	"net/http"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestQuery(t *testing.T) {
	assertQuery(t,
		"?timeout=10s&yql=select+from+sources+%2A+where+title+contains+%27foo%27",
		"select from sources * where title contains 'foo'")
}

func TestQueryVerbose(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, "{\"query\":\"result\"}")

	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	assert.Nil(t, cli.Run("-t", "http://127.0.0.1:8080", "query", "-v", "select from sources * where title contains 'foo'"))
	assert.Equal(t, "curl 'http://127.0.0.1:8080/search/?timeout=10s&yql=select+from+sources+%2A+where+title+contains+%27foo%27'\n", stderr.String())
	assert.Equal(t, "{\n    \"query\": \"result\"\n}\n", stdout.String())
}

func TestQueryUnformatted(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, "{\"query\":\"result\"}")
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client

	assert.Nil(t, cli.Run("-t", "http://127.0.0.1:8080", "--format=plain", "query", "select from sources * where title contains 'foo'"))
	assert.Equal(t, "{\"query\":\"result\"}\n", stdout.String())
}

func TestQueryNonJsonResult(t *testing.T) {
	assertQuery(t,
		"?timeout=10s&yql=select+from+sources+%2A+where+title+contains+%27foo%27",
		"select from sources * where title contains 'foo'")
}

func TestQueryWithMultipleParameters(t *testing.T) {
	assertQuery(t,
		"?hits=5&timeout=20s&yql=select+from+sources+%2A+where+title+contains+%27foo%27+and+year+%3D+2000",
		"select from sources * where title contains 'foo' and year = 2000", "hits=5", "timeout=20s")
}

func TestQueryWithEquals(t *testing.T) {
	assertQuery(t,
		"?timeout=10s&yql=SELECT+from+sources+%2A+where+title+contains+%27foo%27+and+year+%3D+2000",
		"SELECT from sources * where title contains 'foo' and year = 2000")
}

func TestQuerySelect(t *testing.T) {
	assertQuery(t,
		"?hits=5&select=%7B%22select%22%3A%7B%22where%22%3A%7B%22contains%22%3A%5B%22title%22%2C%22a%22%5D%7D%7D%7D&timeout=10s",
		`select={"select":{"where":{"contains":["title","a"]}}}`, "hits=5")
}

func TestQueryWithExplicitYqlParameter(t *testing.T) {
	assertQuery(t,
		"?timeout=10s&yql=select+from+sources+%2A+where+title+contains+%27foo%27",
		"yql=select from sources * where title contains 'foo'")
}

func TestIllegalQuery(t *testing.T) {
	assertQueryError(t, 401, "query error message")
}

func TestServerError(t *testing.T) {
	assertQueryServiceError(t, 501, "server error message")
}

func TestQueryHeader(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, "{\"query\":\"result\"}")

	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client

	assert.Nil(t, cli.Run("-t", "http://127.0.0.1:8080", "query",
		"--header", "X-Foo: bar",
		"--header", "X-Foo: baz",
		"--header", "X-Bar:   foo bar  ",
		"select from sources * where title contains 'foo'"))
	assert.Equal(t, []string{"bar", "baz"}, client.LastRequest.Header.Values("X-Foo"))
	assert.Equal(t, "foo bar", client.LastRequest.Header.Get("X-Bar"))

	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "query",
		"--header", "X-Foo", "select from sources * where title contains 'foo'"))
	assert.Equal(t, "Error: invalid header \"X-Foo\": missing colon separator\n", stderr.String())
}

func TestStreamingQuery(t *testing.T) {
	body := `
event: token
data: {"token": "The"}

event: token
data: {"token": "Manhattan"}

event: token
data: {"token": "Project"}

event: end
`
	assertStreamingQuery(t, "The Manhattan Project\n", body)
	assertStreamingQuery(t, body, body, "--format=plain")

	bodyWithError := `
event: token
data: {"token": "The"}

event: token
data: Manhattan

event: error
data: {"message": "something went wrong"}
`
	assertStreamingQuery(t, `The Manhattan
event: error
data: {
    "message": "something went wrong"
}
`, bodyWithError)
	assertStreamingQuery(t, bodyWithError, bodyWithError, "--format=plain")
}

func assertStreamingQuery(t *testing.T, expectedOutput, body string, args ...string) {
	t.Helper()
	client := &mock.HTTPClient{}
	response := mock.HTTPResponse{Status: 200, Header: make(http.Header)}
	response.Header.Set("Content-Type", "text/event-stream")
	response.Body = []byte(body)
	client.NextResponse(response)
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	assert.Nil(t, cli.Run(append(args, "-t", "http://127.0.0.1:8080", "query", "select something")...))
	assert.Equal(t, "", stderr.String())
	assert.Equal(t, expectedOutput, stdout.String())
}

func assertStreamingQueryErr(t *testing.T, expectedOut, expectedErr, body string, args ...string) {
	t.Helper()
	client := &mock.HTTPClient{}
	response := mock.HTTPResponse{Status: 200, Header: make(http.Header)}
	response.Header.Set("Content-Type", "text/event-stream")
	response.Body = []byte(body)
	client.NextResponse(response)
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	assert.NotNil(t, cli.Run(append(args, "-t", "http://127.0.0.1:8080", "query", "select something")...))
	assert.Equal(t, expectedErr, stderr.String())
	assert.Equal(t, expectedOut, stdout.String())
}

func assertQuery(t *testing.T, expectedQuery string, query ...string) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, "{\"query\":\"result\"}")
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client

	args := []string{"-t", "http://127.0.0.1:8080", "query"}
	assert.Nil(t, cli.Run(append(args, query...)...))
	assert.Equal(t,
		"{\n    \"query\": \"result\"\n}\n",
		stdout.String(),
		"query output")
	queryURL, err := queryServiceURL(client)
	require.Nil(t, err)
	assert.Equal(t, queryURL+"/search/"+expectedQuery, client.LastRequest.URL.String())
}

func assertQueryError(t *testing.T, status int, errorMessage string) {
	client := &mock.HTTPClient{}
	client.NextResponseString(status, errorMessage)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "query", "yql=select from sources * where title contains 'foo'"))
	assert.Equal(t,
		"Error: invalid query: Status "+strconv.Itoa(status)+"\n"+errorMessage+"\n",
		stderr.String(),
		"error output")
}

func assertQueryServiceError(t *testing.T, status int, errorMessage string) {
	client := &mock.HTTPClient{}
	client.NextResponseString(status, errorMessage)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client
	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "query", "yql=select from sources * where title contains 'foo'"))
	assert.Equal(t,
		"Error: Status "+strconv.Itoa(status)+" from container at 127.0.0.1:8080\n"+errorMessage+"\n",
		stderr.String(),
		"error output")
}

func queryServiceURL(client *mock.HTTPClient) (string, error) {
	return "http://127.0.0.1:8080", nil
}
