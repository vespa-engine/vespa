// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
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
