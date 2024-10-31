// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func createComplexValue(t *testing.T) Value {
	val := FromJson("{" +
		"a:[17,{x:{z:[1,2]},y:[3,4,5]},['foo','bar']]," +
		"b:{x:[6,7,8],y:[9,10]}," +
		"c:11" +
		"}")
	assert.True(t, val.Valid())
	return val
}

func TestSelector(t *testing.T) {
	x := createComplexValue(t)
	s1 := &selectField{"a"}
	s2 := &selectField{"y"}
	s3 := &selectEntry{0}
	s4 := &selectEntry{1}
	x = s1.Select(x)
	x = s4.Select(x)
	x = s2.Select(x)
	y := s3.Select(x)
	z := s4.Select(x)
	verifyLong(3)(t, y)
	verifyLong(4)(t, z)
	y = noSelector.Select(y)
	z = noSelector.Select(z)
	verifyLong(3)(t, y)
	verifyLong(4)(t, z)
}

func TestPath(t *testing.T) {
	root := createComplexValue(t)
	path := NewPath()
	path.Field("a")
	path.Entry(1)
	path.Field("y")
	path.Entry(0)
	verifyLong(3)(t, path.Apply(root))
	path2 := path.Clone()
	assert.Equal(t, 4, path2.Len())
	path2.Trim(1)
	assert.Equal(t, 3, path2.Len())
	path2.Entry(1)
	verifyLong(3)(t, path.Apply(root))
	verifyLong(4)(t, path2.Apply(root))
	path.Trim(3)
	path2.Trim(100)
	assert.Equal(t, 1, path.Len())
	assert.Equal(t, 0, path2.Len())
}

func TestPathAt(t *testing.T) {
	path := NewPath()
	path.Entry(7)
	path.Field("foo")
	path.Entry(5)
	path.Field("bar")
	assert.True(t, path.At(0).WouldSelectEntry(7))
	assert.False(t, path.At(0).WouldSelectEntry(5))
	assert.True(t, path.At(1).WouldSelectField("foo"))
	assert.False(t, path.At(1).WouldSelectField("bar"))
	assert.True(t, path.At(2).WouldSelectEntry(5))
	assert.False(t, path.At(2).WouldSelectEntry(7))
	assert.True(t, path.At(3).WouldSelectField("bar"))
	assert.False(t, path.At(3).WouldSelectField("foo"))
	assert.False(t, path.At(4).WouldSelectField(""))
	assert.False(t, path.At(4).WouldSelectEntry(0))
	assert.True(t, path.At(-1).WouldSelectField("bar"))
	assert.False(t, path.At(-1).WouldSelectField("foo"))
	assert.True(t, path.At(-2).WouldSelectEntry(5))
	assert.False(t, path.At(-2).WouldSelectEntry(7))
	assert.True(t, path.At(-3).WouldSelectField("foo"))
	assert.False(t, path.At(-3).WouldSelectField("bar"))
	assert.True(t, path.At(-4).WouldSelectEntry(7))
	assert.False(t, path.At(-4).WouldSelectEntry(5))
	assert.False(t, path.At(-5).WouldSelectField(""))
	assert.False(t, path.At(-5).WouldSelectEntry(0))
}

func TestFindMultipleFields(t *testing.T) {
	root := createComplexValue(t)
	res := Find(root, func(path *Path, value Value) bool {
		return path.At(-1).WouldSelectField("y")
	})
	arr2 := Invalid
	arr3 := Invalid
	assert.Equal(t, 2, len(res))
	for _, path := range res {
		v := path.Apply(root)
		if v.NumEntries() == 2 {
			arr2 = v
		}
		if v.NumEntries() == 3 {
			arr3 = v
		}
	}
	verifyArray([]verifyValue{verifyLong(9), verifyLong(10)})(t, arr2)
	verifyArray([]verifyValue{verifyLong(3), verifyLong(4), verifyLong(5)})(t, arr3)
}
