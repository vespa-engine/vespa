// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// query command tests
// Author: bratseth

package cmd

import (
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestQuery(t *testing.T) {
	assertQuery(t,
		"?yql=select+from+sources+%2A+where+title+contains+%27foo%27",
		"select from sources * where title contains 'foo'")
}

func TestQueryNonJsonResult(t *testing.T) {
	assertQuery(t,
		"?yql=select+from+sources+%2A+where+title+contains+%27foo%27",
		"select from sources * where title contains 'foo'")
}

func TestQueryWithMultipleParameters(t *testing.T) {
	assertQuery(t,
		"?hits=5&yql=select+from+sources+%2A+where+title+contains+%27foo%27",
		"select from sources * where title contains 'foo'", "hits=5")
}

func TestQueryWithExplicitYqlParameter(t *testing.T) {
	assertQuery(t,
		"?yql=select+from+sources+%2A+where+title+contains+%27foo%27",
		"yql=select from sources * where title contains 'foo'")
}

func TestIllegalQuery(t *testing.T) {
	assertQueryError(t, 401, "query error message")
}

func TestServerError(t *testing.T) {
	assertQueryServiceError(t, 501, "server error message")
}

func assertQuery(t *testing.T, expectedQuery string, query ...string) {
	client := &mockHttpClient{}
	client.NextResponse(200, "{\"query\":\"result\"}")
	assert.Equal(t,
		"{\n    \"query\": \"result\"\n}\n",
		executeCommand(t, client, []string{"query"}, query),
		"query output")
	queryURL := queryServiceURL(client)
	assert.Equal(t, queryURL+"/search/"+expectedQuery, client.lastRequest.URL.String())
}

func assertQueryError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{}
	client.NextResponse(status, errorMessage)
	_, outErr := execute(command{args: []string{"query", "yql=select from sources * where title contains 'foo'"}}, t, client)
	assert.Equal(t,
		"Error: Invalid query: Status "+strconv.Itoa(status)+"\n"+errorMessage+"\n",
		outErr,
		"error output")
}

func assertQueryServiceError(t *testing.T, status int, errorMessage string) {
	client := &mockHttpClient{}
	client.NextResponse(status, errorMessage)
	_, outErr := execute(command{args: []string{"query", "yql=select from sources * where title contains 'foo'"}}, t, client)
	assert.Equal(t,
		"Error: Status "+strconv.Itoa(status)+" from container at 127.0.0.1:8080\n"+errorMessage+"\n",
		outErr,
		"error output")
}

func queryServiceURL(client *mockHttpClient) string {
	return getService("query", 0).BaseURL
}
