// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// query command tests
// Author: bratseth

package cmd

import (
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

	assert.Nil(t, cli.Run("query", "-v", "select from sources * where title contains 'foo'"))
	assert.Equal(t, "curl 'http://127.0.0.1:8080/search/?timeout=10s&yql=select+from+sources+%2A+where+title+contains+%27foo%27'\n", stderr.String())
	assert.Equal(t, "{\n    \"query\": \"result\"\n}\n", stdout.String())
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

func assertQuery(t *testing.T, expectedQuery string, query ...string) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, "{\"query\":\"result\"}")
	cli, stdout, _ := newTestCLI(t)
	cli.httpClient = client

	args := []string{"query"}
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
	assert.NotNil(t, cli.Run("query", "yql=select from sources * where title contains 'foo'"))
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
	assert.NotNil(t, cli.Run("query", "yql=select from sources * where title contains 'foo'"))
	assert.Equal(t,
		"Error: Status "+strconv.Itoa(status)+" from container at 127.0.0.1:8080\n"+errorMessage+"\n",
		stderr.String(),
		"error output")
}

func queryServiceURL(client *mock.HTTPClient) (string, error) {
	return "http://127.0.0.1:8080", nil
}
