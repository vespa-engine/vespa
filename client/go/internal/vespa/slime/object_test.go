// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func dummyFieldNames(n int) []string {
	fields := make([]string, n)
	for i := 0; i < n; i++ {
		fields[i] = string(rune('a' + i))
	}
	return fields
}

func TestObject(t *testing.T) {
	obj := Object()
	input := leafTestValues()
	names := dummyFieldNames(len(input))
	actual := make(map[string]Value)
	for i, name := range names {
		actual[name] = obj.Set(name, input[i])
	}

	expect := map[string]expectLeaf{
		"a": {mytype: EMPTY},
		"b": {mytype: BOOL, boolVal: true},
		"c": {mytype: LONG, longVal: 5, doubleVal: 5},
		"d": {mytype: DOUBLE, longVal: 5, doubleVal: 5.5},
		"e": {mytype: STRING, stringVal: "foo"},
		"f": {mytype: DATA, dataVal: []byte{1, 2, 3}},
	}

	collect := make(map[string]Value)
	obj.EachField(func(name string, val Value) {
		collect[name] = val
	})

	assert.Equal(t, obj.NumFields(), len(expect))
	assert.Equal(t, len(collect), len(expect))
	for n, e := range expect {
		checkLeaf(t, actual[n], e)
		checkLeaf(t, collect[n], e)
		checkLeaf(t, obj.Field(n), e)
	}
}

func TestOverwriteField(t *testing.T) {
	obj := Object()

	obj.Set("a", Long(10))
	initialExpect := expectLeaf{mytype: LONG, longVal: 10, doubleVal: 10}
	checkLeaf(t, obj.Field("a"), initialExpect)

	obj.Set("a", String("updated"))
	newExpect := expectLeaf{mytype: STRING, stringVal: "updated"}
	checkLeaf(t, obj.Field("a"), newExpect)
}
