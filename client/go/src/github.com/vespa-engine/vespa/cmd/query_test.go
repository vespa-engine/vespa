// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// query command tests
// Author: bratseth

package cmd

import (
    "github.com/stretchr/testify/assert"
    "testing"
)

func TestSimpleQuery(t *testing.T) {
    reset()

    nextBody = "query result"
    query := "select from * where title contains foo"
	assert.Equal(t,
	             "query result\n",
	             executeCommand(t, []string{"query", "?query=" + query},[]string{}),
	             "query output")
    assert.Equal(t, GetTarget(queryContext).query + "/search/?query=" + query, lastRequest.URL.String())
}

func TestQueryWithParameters(t *testing.T) {
    reset()

    nextBody = "query result"
    query := "select from * where title contains foo"
	assert.Equal(t,
	             "query result\n",
	             executeCommand(t, []string{"query", "?hits=4&query=" + query},[]string{}),
	             "query output")
    assert.Equal(t, GetTarget(queryContext).query + "/search/?hits=4&query=" + query, lastRequest.URL.String())
}

func TestSimpleQueryMissingQuestionMark(t *testing.T) {
    reset()

    nextBody = "query result"
    query := "select from * where title contains foo"
	assert.Equal(t,
	             "query result\n",
	             executeCommand(t, []string{"query", "query=" + query},[]string{}),
	             "query output")
    assert.Equal(t, GetTarget(queryContext).query + "/search/?query=" + query, lastRequest.URL.String())
}

func TestSimpleQueryMissingQuestionMarkAndQueryEquals(t *testing.T) {
    reset()

    nextBody = "query result"
    query := "select from * where title contains foo"
	assert.Equal(t,
	             "query result\n",
	             executeCommand(t, []string{"query", query},[]string{}),
	             "query output")
    assert.Equal(t, GetTarget(queryContext).query + "/search/?query=" + query, lastRequest.URL.String())
}
