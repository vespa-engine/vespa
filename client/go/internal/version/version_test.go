// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package version

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestParse(t *testing.T) {
	_, err := Parse("foo")
	assert.NotNil(t, err)

	v, err := Parse("1.2.3")
	assert.Nil(t, err)
	assert.Equal(t, "1.2.3", v.String())

	v, err = Parse("v4.5.6")
	assert.Nil(t, err)
	assert.Equal(t, "4.5.6", v.String())

	v, err = Parse("1.2.3-foo")
	assert.Nil(t, err)
	assert.Equal(t, "1.2.3-foo", v.String())
}

func TestIsZero(t *testing.T) {
	v, err := Parse("0.0.0")
	assert.Nil(t, err)
	assert.True(t, v.IsZero())

	v, err = Parse("0.0.0-foo")
	assert.Nil(t, err)
	assert.True(t, v.IsZero())

	v, err = Parse("1.2.3")
	assert.Nil(t, err)
	assert.False(t, v.IsZero())
}

func TestCompare(t *testing.T) {
	assertComparison(t, "1.2.3", '>', "1.0.0")
	assertComparison(t, "1.0.0", '<', "1.2.3")

	assertComparison(t, "1.2.3", '=', "1.2.3")
	assertComparison(t, "1.2.3", '>', "1.2.0")
	assertComparison(t, "1.2.0", '<', "1.2.3")
	assertComparison(t, "1.2.3", '>', "1.2.0")
	assertComparison(t, "1.0.3", '<', "1.2.3")

	assertComparison(t, "1.2.3", '>', "1.1.4")
	assertComparison(t, "1.1.4", '<', "1.2.3")

	assertComparison(t, "2.0.0", '>', "1.2.3")
	assertComparison(t, "1.2.3", '<', "2.0.0")

	assertComparison(t, "1.2.3-alpha1", '<', "1.2.3")
	assertComparison(t, "1.2.3", '>', "1.2.3-alpha1")
	assertComparison(t, "1.2.3-alpha1", '=', "1.2.3-alpha1")
	assertComparison(t, "1.2.3-alpha1", '<', "1.2.3-alpha2")
	assertComparison(t, "1.2.3-alpha2", '>', "1.2.3-alpha1")
}

func assertComparison(t *testing.T, s1 string, cmp rune, s2 string) {
	v1, err := Parse(s1)
	assert.Nil(t, err)
	v2, err := Parse(s2)
	assert.Nil(t, err)
	result := v1.Compare(v2)
	switch cmp {
	case '<':
		assert.True(t, result < 0, fmt.Sprintf("%s is less than %s", v1, v2))
	case '>':
		assert.True(t, result > 0, fmt.Sprintf("%s is greater than %s", v1, v2))
	case '=':
		assert.True(t, result == 0, fmt.Sprintf("%s is equal to %s", v1, v2))
	default:
		t.Fatal("invalid comparator: %r", cmp)
	}
}
