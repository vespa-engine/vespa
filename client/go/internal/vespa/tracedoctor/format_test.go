// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestWord(t *testing.T) {
	assert.Equal(t, "child", word(1, "child", "children"))
	assert.Equal(t, "children", word(2, "child", "children"))
	assert.Equal(t, "children", word(0, "child", "children"))
}

func TestSuffix(t *testing.T) {
	assert.Equal(t, "", suffix(1, "s"))
	assert.Equal(t, "s", suffix(2, "s"))
	assert.Equal(t, "s", suffix(0, "s"))
}

func TestRenderTable(t *testing.T) {
	tab := newTable("1", "a")
	tab.addRow("b", "2")
	tab.addRow("123456", "abcdef")
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
	tab := newTable("1", "empty")
	tab.addRow()
	tab.addRow("123456")
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
	tab := newTableNoHeaders(2)
	tab.addRow("b", "2")
	tab.addRow("123456", "abcdef")
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
	tab := newTable("1", "a")
	tab.addRow("b", "2")
	tab.addLine()
	tab.addRow("123456", "abcdef")
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
