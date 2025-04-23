// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"bytes"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestRenderTable(t *testing.T) {
	tab := newTable().str("1").str("a").commit().line()
	tab.str("b").str("2").commit()
	tab.str("123456").str("abcdef").commit()
	var buf bytes.Buffer
	tab.render(&output{out: &buf})
	expected := "" +
		"┌────────┬────────┐\n" +
		"│      1 │ a      │\n" +
		"├────────┼────────┤\n" +
		"│ b      │      2 │\n" +
		"│ 123456 │ abcdef │\n" +
		"└────────┴────────┘\n"
	assert.Equal(t, expected, buf.String())
}

func TestRenderTableRowPadding(t *testing.T) {
	tab := newTable().str("1").str("empty").commit().line()
	tab.commit()
	tab.str("123456").commit()
	var buf bytes.Buffer
	tab.render(&output{out: &buf})
	expected := "" +
		"┌────────┬───────┐\n" +
		"│      1 │ empty │\n" +
		"├────────┼───────┤\n" +
		"│        │       │\n" +
		"│ 123456 │       │\n" +
		"└────────┴───────┘\n"
	assert.Equal(t, expected, buf.String())
}

func TestRenderTableNoHeaders(t *testing.T) {
	tab := newTable().str("b").str("2").commit()
	tab.str("123456").str("abcdef").commit()
	var buf bytes.Buffer
	tab.render(&output{out: &buf})
	expected := "" +
		"┌────────┬────────┐\n" +
		"│ b      │      2 │\n" +
		"│ 123456 │ abcdef │\n" +
		"└────────┴────────┘\n"
	assert.Equal(t, expected, buf.String())
}

func TestRenderTableWithExtraLine(t *testing.T) {
	tab := newTable().str("1").str("a").commit().line()
	tab.str("b").str("2").commit().line()
	tab.str("123456").str("abcdef").commit()
	var buf bytes.Buffer
	tab.render(&output{out: &buf})
	expected := "" +
		"┌────────┬────────┐\n" +
		"│      1 │ a      │\n" +
		"├────────┼────────┤\n" +
		"│ b      │      2 │\n" +
		"├────────┼────────┤\n" +
		"│ 123456 │ abcdef │\n" +
		"└────────┴────────┘\n"
	assert.Equal(t, expected, buf.String())
}

func TestRenderNestedTables(t *testing.T) {
	obj := makeTable(func(t *table) {
		t.str("key").str("value").commit().line()
		t.str("a").str("1").commit()
		t.str("b").str("2").commit()
		t.str("c").str("3").commit()
	})
	arr := makeTable(func(t *table) {
		t.str("value").commit().line()
		t.str("1").commit()
		t.str("2").commit()
		t.str("3").commit()
	})
	tab := newTable().str("type").str("nested").commit().line()
	tab.str("object").tab(obj).commit()
	tab.str("array").tab(arr).commit()
	var buf bytes.Buffer
	tab.render(&output{out: &buf})
	expected := "" +
		"┌────────┬─────────────┐\n" +
		"│ type   │ nested      │\n" +
		"├────────┼─────┬───────┤\n" +
		"│ object │ key │ value │\n" +
		"│        ├─────┼───────┤\n" +
		"│        │ a   │     1 │\n" +
		"│        │ b   │     2 │\n" +
		"│        │ c   │     3 │\n" +
		"├────────┼─────┴───────┤\n" +
		"│ array  │ value       │\n" +
		"│        ├─────────────┤\n" +
		"│        │           1 │\n" +
		"│        │           2 │\n" +
		"│        │           3 │\n" +
		"└────────┴─────────────┘\n"
	assert.Equal(t, expected, buf.String())
}

func TestRenderTableWithWideCharacters(t *testing.T) {
	tab := newTable().
		str("🍤").commit().
		str("").str("🍟").commit().
		str("").str("").str("ツ").commit().
		str("x").str("x").str("x").commit().
		str("1").str("1").str("1").commit()
	var buf bytes.Buffer
	tab.render(&output{out: &buf})
	expected := "" +
		"┌────┬────┬────┐\n" +
		"│ 🍤 │    │    │\n" +
		"│    │ 🍟 │    │\n" +
		"│    │    │ ツ │\n" +
		"│ x  │ x  │ x  │\n" +
		"│  1 │  1 │  1 │\n" +
		"└────┴────┴────┘\n"
	assert.Equal(t, expected, buf.String())
}
