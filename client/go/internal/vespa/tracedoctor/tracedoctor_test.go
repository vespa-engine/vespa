package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestPercentageOfQuery(t *testing.T) {
	tests := []struct {
		timing    *timing
		parameter float64
		expected  string
	}{
		{&timing{queryMs: 200}, 50, " (25.00% of query time)"},
		{&timing{queryMs: 100}, 100, ""},
		{&timing{queryMs: 100}, 150, ""},
		{nil, 50, ""},
		{&timing{queryMs: 0}, 50, ""},
		{&timing{queryMs: 200}, 0, " (0.00% of query time)"},
	}

	for _, tt := range tests {
		assert.Equal(t, tt.expected, tt.timing.percentageOfQuery(tt.parameter))
	}
}
