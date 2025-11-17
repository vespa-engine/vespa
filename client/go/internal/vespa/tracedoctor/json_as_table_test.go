// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"bufio"
	"os"
	"testing"

	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

// experimenting with rendering json as nested table
// when standing in this directory, run with:
// JSON_AS_TABLE_PARAM=<json-file> go test -run TestJsonAsTable
func TestJsonAsTable(notUsed *testing.T) {
	in := os.Getenv("JSON_AS_TABLE_PARAM")
	if len(in) == 0 {
		return
	}
	file, _ := os.Open(in)
	defer file.Close()
	root := slime.DecodeJson(bufio.NewReaderSize(file, 64*1024))
	slimeValueAsTable(root).render(&output{out: os.Stdout})
}
