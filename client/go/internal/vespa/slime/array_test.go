// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func leafTestValues() []Value {
	return []Value{
		Empty,
		Bool(true),
		Long(5),
		Double(5.5),
		String("foo"),
		Data([]byte{1, 2, 3}),
	}
}

func TestArray(t *testing.T) {
	arr := Array()
	input := leafTestValues()
	actual := make([]Value, len(input))
	for i, v := range input {
		actual[i] = arr.Add(v)
	}

	expect := []expectLeaf{
		expectLeaf{mytype: EMPTY},
		expectLeaf{mytype: BOOL, boolVal: true},
		expectLeaf{mytype: LONG, longVal: 5, doubleVal: 5},
		expectLeaf{mytype: DOUBLE, longVal: 5, doubleVal: 5.5},
		expectLeaf{mytype: STRING, stringVal: "foo"},
		expectLeaf{mytype: DATA, dataVal: []byte{1, 2, 3}}}

	var expectIndex int
	var collect []Value
	arr.EachEntry(func(idx int, val Value) {
		assert.Equal(t, idx, expectIndex)
		collect = append(collect, val)
		expectIndex++
	})

	assert.Equal(t, arr.NumEntries(), len(expect))
	for i, e := range expect {
		checkLeaf(t, actual[i], e)
		checkLeaf(t, collect[i], e)
		checkLeaf(t, arr.Entry(i), e)
	}
}
