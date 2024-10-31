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
}

func TestInsertEntry(t *testing.T) {
	arr := Array()
	in := InsertEntry(arr)
	res := in.Insert(Long(5))
	assert.Equal(t, int64(5), arr.Entry(0).AsLong())
	assert.Equal(t, int64(5), res.AsLong())
}

func TestInsertField(t *testing.T) {
	obj := Object()
	in := InsertField(obj, "foo")
	res := in.Insert(Long(5))
	assert.Equal(t, int64(5), obj.Field("foo").AsLong())
	assert.Equal(t, int64(5), res.AsLong())
}
