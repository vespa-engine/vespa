// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// query command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestQuery(t *testing.T) {
    assertQuery(t,
                "?yql=select+from+sources+%2A+where+title+contains+%27foo%27",
                "select from sources * where title contains 'foo'")
}

func TestQueryWithMultipleParameters(t *testing.T) {
    assertQuery(t,
                "?yql=select+from+sources+%2A+where+title+contains+%27foo%27&hits=5",
                "select from sources * where title contains 'foo'", "hits=5")
}

func TestQueryWithExplicitYqlParameter(t *testing.T) {
    assertQuery(t,
                "?yql=select+from+sources+%2A+where+title+contains+%27foo%27",
                "yql=select from sources * where title contains 'foo'")
}

func assertQuery(t *testing.T, expectedQuery string, query ...string) {
    client := &mockHttpClient{ nextBody: "query result", }
	assert.Equal(t,
	             "query result\n",
	             executeCommand(t, client, []string{"query"}, query),
	             "query output")
    assert.Equal(t, getTarget(queryContext).query + "/search/" + expectedQuery, client.lastRequest.URL.String())
}
