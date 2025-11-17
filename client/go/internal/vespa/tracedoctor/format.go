// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"
	"sort"

	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

func word(n int, singular, plural string) string {
	if n == 1 {
		return singular
	}
	return plural
}

func suffix(n int, s string) string {
	return word(n, "", s)
}

func renderSlimeValueIntoTable(v slime.Value, t *table) {
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
		t.str(fmt.Sprintf("%v", v.AsLong())).commit()
	case slime.DOUBLE:
		t.str(fmt.Sprintf("%v", v.AsDouble())).commit()
	case slime.STRING:
		t.str(v.AsString()).commit()
	case slime.DATA:
		t.str(fmt.Sprintf("0x%x", v.AsData())).commit()
	case slime.ARRAY:
		inner := newTable()
		v.EachEntry(func(idx int, e slime.Value) {
			renderSlimeValueIntoTable(e, inner)
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
			renderSlimeValueIntoTable(f.val, inner)
		}
		t.tab(inner).commit()
	}
}

func slimeValueAsTable(v slime.Value) *table {
	t := newTable()
	renderSlimeValueIntoTable(v, t)
	return t
}

func renderQueryNodes(q *queryTree, ids []int, out *output) {
	for _, id := range ids {
		if node, exists := q.index[id]; exists {
			out.fmt("query node %d: %s\n", id, node.desc())
			node.asTable().render(out)
		} else {
			out.fmt("query node %d not found\n", id)
		}
	}
}
