// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"bufio"
	"fmt"
	"os"
	"sort"
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
	var insert func(v slime.Value, t *table)
	insert = func(v slime.Value, t *table) {
		switch v.Type() {
		case slime.EMPTY:
			t.str("null").commit()
		case slime.BOOL:
			if v.AsBool() {
				t.str("true").commit()
			} else {
				t.str("false").commit()
			}
		case slime.LONG:
			t.str(fmt.Sprintf("%d", v.AsLong())).commit()
		case slime.DOUBLE:
			t.str(fmt.Sprintf("%f", v.AsDouble())).commit()
		case slime.STRING:
			t.str(v.AsString()).commit()
		case slime.DATA:
			t.str(fmt.Sprintf("%x", v.AsData())).commit()
		case slime.ARRAY:
			inner := newTable()
			v.EachEntry(func(idx int, e slime.Value) {
				insert(e, inner)
			})
			t.tab(inner).commit()
		case slime.OBJECT:
			type field struct {
				name string
				val  slime.Value
			}
			var fields []field
			v.EachField(func(name string, f slime.Value) {
				fields = append(fields, field{name, f})
			})
			sort.Slice(fields, func(i, j int) bool {
				typeRank := func(v slime.Value) int {
					switch v.Type() {
					case slime.OBJECT:
						return 1
					case slime.ARRAY:
						return 2
					default:
						return 0
					}
				}
				ri, rj := typeRank(fields[i].val), typeRank(fields[j].val)
				if ri != rj {
					return ri < rj
				}
				return fields[i].name < fields[j].name
			})
			inner := newTable()
			for _, f := range fields {
				inner.str(f.name)
				insert(f.val, inner)
			}
			t.tab(inner).commit()
		}
	}
	tab := newTable()
	insert(root, tab)
	renderCell(&cellFrame{tab}).render(&output{out: os.Stdout})
}
