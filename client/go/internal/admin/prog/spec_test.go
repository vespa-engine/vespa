// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package prog

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestProgSpec(t *testing.T) {
	spec := NewSpec([]string{"/opt/vespa/bin/foobar"})
	var b bool

	b = spec.matchesListString("")
	assert.Equal(t, false, b)
	b = spec.matchesListString("foobar")
	assert.Equal(t, true, b)
	b = spec.matchesListString("foo bar")
	assert.Equal(t, false, b)
	b = spec.matchesListString("one foobar")
	assert.Equal(t, true, b)
	b = spec.matchesListString("foobar two")
	assert.Equal(t, true, b)
	b = spec.matchesListString("one foobar two")
	assert.Equal(t, true, b)
	b = spec.matchesListString("all")
	assert.Equal(t, true, b)

	var s string
	s = spec.valueFromListString("")
	assert.Equal(t, "", s)
	s = spec.valueFromListString("foobar=123")
	assert.Equal(t, "123", s)
	s = spec.valueFromListString("one=1 foobar=123 two=2")
	assert.Equal(t, "123", s)
	s = spec.valueFromListString("one=1 all=123")
	assert.Equal(t, "123", s)
}

type strVec []string

func (v strVec) contains(w string) bool {
	for _, val := range v {
		if w == val {
			return true
		}
	}
	return false
}

func TestProgSpecEnv(t *testing.T) {
	spec := NewSpec([]string{"/opt/vespa/bin/foobar"})
	t.Setenv("FOO", "old foo")
	t.Setenv("BAR", "bar")
	spec.Setenv("FOO", "foo")
	assert.Equal(t, "foo", spec.Getenv("FOO"))
	assert.Equal(t, "bar", spec.Getenv("BAR"))
	envv := strVec(spec.EffectiveEnv())
	assert.True(t, envv.contains("FOO=foo"))
	assert.True(t, envv.contains("BAR=bar"))
	assert.False(t, envv.contains("FOO=old foo"))
}
