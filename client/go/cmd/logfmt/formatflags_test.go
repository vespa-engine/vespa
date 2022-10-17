package logfmt

import (
	"fmt"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestOutputFormat(t *testing.T) {
	type args struct {
		val string
	}
	tests := []struct {
		expected OutputFormat
		arg      string
		wantErr  assert.ErrorAssertionFunc
	}{
		{FormatVespa, "vespa", assert.NoError},
		{FormatRaw, "raw", assert.NoError},
		{FormatJSON, "json", assert.NoError},
		{-1, "foo", assert.Error},
	}
	for _, tt := range tests {
		t.Run(tt.arg, func(t *testing.T) {
			var v OutputFormat = -1
			tt.wantErr(t, v.Set(tt.arg), fmt.Sprintf("Set(%v)", tt.arg))
			assert.Equal(t, v, tt.expected)
		})
	}
}
