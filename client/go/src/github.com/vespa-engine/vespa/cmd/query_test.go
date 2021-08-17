// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// query command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestQuery(t *testing.T) {
    assertQuery("", "?query=select from sources * where title contains 'foo'&hits=5", t)
}

func TestQueryWithParameters(t *testing.T) {
    assertQuery("?", "query=select from sources * where title contains 'foo'&hits=5", t)
}

func TestSimpleQueryMissingQuestionMark(t *testing.T) {
    assertQuery("?", "query=select from sources * where title contains 'foo'", t)
}

func TestSimpleQueryMissingQuestionMarkAndQueryEquals(t *testing.T) {
    assertQuery("?query=", "select from sources * where text contains 'foo'", t)
}

func assertQuery(expectedPrefix string, query string, t *testing.T) {
    reset()

    nextBody = "query result"
	assert.Equal(t,
	             "query result\n",
	             executeCommand(t, []string{"query", query},[]string{}),
	             "query output")
    assert.Equal(t, getTarget(queryContext).query + "/search/" + expectedPrefix + query, lastRequest.URL.String())
}
