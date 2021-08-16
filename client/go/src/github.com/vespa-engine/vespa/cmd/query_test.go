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

    query := "select * from foo where ..."
	assert.Equal(t,
	             "",
	             executeCommand(t, []string{"query", "?query=" + query},[]string{}),
	             "simple query")
    assert.Equal(t, GetTarget(queryContext).query + "/search/?query=" + query, lastRequest.URL.String())
}

