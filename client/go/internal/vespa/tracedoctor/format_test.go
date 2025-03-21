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

func TestRenderTableNoHeaders(t *testing.T) {
	tab := newTable("", "")
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
