// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package logfmt

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestOutputFormat(t *testing.T) {
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
