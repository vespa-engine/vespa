// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"testing"
)

func assertMatch(t *testing.T, re string, flag *regexFlag, texts ...string) {
	t.Helper()
	err := flag.Set(re)
	require.Nil(t, err, "unexpected error with flag.Set('%s'): %v", re, err)
	assert.Equal(t, re, flag.String(), "set flag displays as '%s', expected '%s'", flag.String(), re)
	for _, text := range texts {
		assert.False(t, flag.unmatched(text), "flag '%s' claims a non-match for '%s'", flag.String(), text)
	}
}

func assertUnmatch(t *testing.T, re string, flag *regexFlag, texts ...string) {
	t.Helper()
	err := flag.Set(re)
	require.Nil(t, err, "unexpected error with flag.Set('%s'): %v", re, err)
	assert.Equal(t, re, flag.String())
	for _, text := range texts {
		assert.True(t, flag.unmatched(text), "flag '%s' should claim a non-match for '%s'", flag.String(), text)
	}
}

func TestRegexFlag(t *testing.T) {
	var flag regexFlag
	assert.Equal(t, "<none>", flag.String())
	assert.Equal(t, "regular expression", flag.Type())
	assert.False(t, flag.unmatched("foobar"), "unset flag claims a non-match")
	assert.EqualError(t, flag.Set("*"), "error parsing regexp: missing argument to repetition operator: `*`")
	assertMatch(t, "foo.*bar", new(regexFlag), "foobar", "foo bar", "x foobar y", "xfoobary", "xfooybarz")
	assertUnmatch(t, "foo.*bar", new(regexFlag), "Foobar", "foo Bar", "fxoobar", "whatever")
}
