// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestArrayApi1(t *testing.T) {
	v := ArrayList[string]{"x", "y", "z"}
	assert.Equal(t, 3, len(v))
	v.Append("a")
	assert.Equal(t, 4, len(v))
	v.Insert(2, "b")
	assert.Equal(t, 5, len(v))
	v.Insert(0, "c")
	assert.Equal(t, 6, len(v))
	assert.Equal(t, "c", v[0])
	assert.Equal(t, "x", v[1])
	assert.Equal(t, "y", v[2])
	assert.Equal(t, "b", v[3])
	assert.Equal(t, "z", v[4])
	assert.Equal(t, "a", v[5])
}

func TestArrayApi2(t *testing.T) {
	tmp := []string{"i", "j", "k"}
	v := NewArrayList[string](10)
	assert.Equal(t, 0, len(v))
	assert.Equal(t, 10, cap(v))
	v.AppendAll(tmp...)
	assert.Equal(t, 3, len(v))
	assert.Equal(t, 10, cap(v))
	v.AppendAll(tmp...)
	assert.Equal(t, 6, len(v))
	assert.Equal(t, 10, cap(v))
	v.AppendAll(tmp...)
	assert.Equal(t, 9, len(v))
	assert.Equal(t, 10, cap(v))
	v.AppendAll(tmp...)
	assert.Equal(t, 12, len(v))
	assert.Less(t, 11, cap(v))
}

func TestArrayApi3(t *testing.T) {
	tmp := []string{"i", "j", "k"}
	v := ArrayList[string]{
		"foo", "bar",
		"baz", "qux",
	}
	assert.Equal(t, 4, len(v))
	v.InsertAll(0, "a", "b")
	assert.Equal(t, 6, len(v))
	v.AppendAll(tmp...)
	assert.Equal(t, 9, len(v))
	v.AppendAll("x", "y")
	assert.Equal(t, 11, len(v))
	v.InsertAll(4, "foobar", "barfoo")
	assert.Equal(t, 13, len(v))
	assert.Equal(t, "a", v[0])
	assert.Equal(t, "b", v[1])
	assert.Equal(t, "foo", v[2])
	assert.Equal(t, "bar", v[3])
	assert.Equal(t, "foobar", v[4])
	assert.Equal(t, "barfoo", v[5])
	assert.Equal(t, "baz", v[6])
	assert.Equal(t, "qux", v[7])
	assert.Equal(t, "i", v[8])
	assert.Equal(t, "j", v[9])
	assert.Equal(t, "k", v[10])
	assert.Equal(t, "x", v[11])
	assert.Equal(t, "y", v[12])
}

func TestArrayApi4(t *testing.T) {
	v := NewArrayList[string](12)
	arr := v[0:10]
	v.InsertAll(0, "a", "b", "e")
	v.InsertAll(3, "f", "g", "o")
	v.InsertAll(2, "c", "d")
	v.InsertAll(7, "h", "i", "j", "k", "l", "m", "n")
	assert.Equal(t, 15, len(v))
	assert.Equal(t, "a", v[0])
	assert.Equal(t, "b", v[1])
	assert.Equal(t, "c", v[2])
	assert.Equal(t, "d", v[3])
	assert.Equal(t, "e", v[4])
	assert.Equal(t, "f", v[5])
	assert.Equal(t, "g", v[6])
	assert.Equal(t, "h", v[7])
	assert.Equal(t, "i", v[8])
	assert.Equal(t, "j", v[9])
	assert.Equal(t, "k", v[10])
	assert.Equal(t, "l", v[11])
	assert.Equal(t, "m", v[12])
	assert.Equal(t, "n", v[13])
	assert.Equal(t, "o", v[14])
	assert.Equal(t, 10, len(arr))
	assert.Equal(t, "a", arr[0])
	assert.Equal(t, "b", arr[1])
	assert.Equal(t, "c", arr[2])
	assert.Equal(t, "d", arr[3])
	assert.Equal(t, "e", arr[4])
	assert.Equal(t, "f", arr[5])
	assert.Equal(t, "g", arr[6])
	assert.Equal(t, "o", arr[7])
	assert.Equal(t, "", arr[8])
	assert.Equal(t, "", arr[9])
}
