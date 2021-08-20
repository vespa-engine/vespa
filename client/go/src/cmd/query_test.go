// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// query command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestQuery(t *testing.T) {
    assertQuery("?yql=select+from+sources+%2A+where+title+contains+%27foo%27",
                "select from sources * where title contains 'foo'", t)
}

func IgnoreTestQueryWithParameters(t *testing.T) {
    assertQuery("?", "select from sources * where title contains 'foo'&hits=5", t)
}

func IgnoreTestSimpleQueryMissingQuestionMark(t *testing.T) {
    assertQuery("?", "query=select from sources * where title contains 'foo'", t)
}

func IgnoreTestSimpleQueryMissingQuestionMarkAndQueryEquals(t *testing.T) {
    assertQuery("?query=", "select from sources * where text contains 'foo'", t)
}

func assertQuery(expectedQuery string, query string, t *testing.T) {
    client := &mockHttpClient{ nextBody: "query result", }
	assert.Equal(t,
	             "query result\n",
	             executeCommand(t, client, []string{"query", query},[]string{}),
	             "query output")
    assert.Equal(t, getTarget(queryContext).query + "/search/" + expectedQuery, client.lastRequest.URL.String())
}
