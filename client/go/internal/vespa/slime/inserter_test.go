// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestInsertRoot(t *testing.T) {
	var root Value
	in := InsertRoot(&root)
	res := in.Insert(Long(5))
	assert.Equal(t, int64(5), root.AsLong())
	assert.Equal(t, int64(5), res.AsLong())
	res2 := in.Insert(String("foo"))
	assert.Equal(t, "foo", root.AsString())
	assert.Equal(t, "foo", res2.AsString())
	assert.Equal(t, int64(5), res.AsLong())
}

func TestInsertEntry(t *testing.T) {
	arr := Array()
	in := InsertEntry(arr)
	assert.Equal(t, 0, arr.NumEntries())
	res := in.Insert(Long(5))
	assert.Equal(t, 1, arr.NumEntries())
	assert.Equal(t, int64(5), arr.Entry(0).AsLong())
	assert.Equal(t, int64(5), res.AsLong())
	res2 := in.Insert(String("foo"))
	assert.Equal(t, 2, arr.NumEntries())
	assert.Equal(t, "foo", arr.Entry(1).AsString())
	assert.Equal(t, "foo", res2.AsString())
	assert.Equal(t, int64(5), res.AsLong())
}

func TestInsertField(t *testing.T) {
	obj := Object()
	in := InsertField(obj, "foo")
	assert.Equal(t, 0, obj.NumFields())
	res := in.Insert(Long(5))
	assert.Equal(t, 1, obj.NumFields())
	assert.Equal(t, int64(5), obj.Field("foo").AsLong())
	assert.Equal(t, int64(5), res.AsLong())
	res2 := in.Insert(String("bar"))
	assert.Equal(t, 1, obj.NumFields())
	assert.Equal(t, "bar", obj.Field("foo").AsString())
	assert.Equal(t, "bar", res2.AsString())
	assert.Equal(t, int64(5), res.AsLong())
}
