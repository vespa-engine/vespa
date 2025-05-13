package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestSplitText(t *testing.T) {
	type testCase struct {
		text     string
		width    int
		expected []string
	}
	tests := []testCase{
		{
			text:     "",
			width:    20,
			expected: []string{""},
		},
		{
			text:     "   ",
			width:    20,
			expected: []string{""},
		},
		{
			text:     "short text",
			width:    20,
			expected: []string{"short text"},
		},
		{
			text:     "this is a test",
			width:    5,
			expected: []string{"this", "is a", "test"},
		},
		{
			text:     "a b c d e f",
			width:    3,
			expected: []string{"a b", "c d", "e f"},
		},
		{
			text:     "ends with space ",
			width:    10,
			expected: []string{"ends with", "space"},
		},
		{
			text:     "a, b, c, d, e",
			width:    5,
			expected: []string{"a, b,", "c, d,", "e"},
		},
		{
			text:     "longwordwithoutspaces",
			width:    5,
			expected: []string{"longw", "ordwi", "thout", "space", "s"},
		},
	}
	for _, tc := range tests {
		got := splitText(tc.text, tc.width)
		assert.Equal(t, tc.expected, got, "text: %q", tc.text)
	}
}
