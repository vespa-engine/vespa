// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestObject(t *testing.T) {
	obj := Object()
	actual := map[string]Value{
		"a": obj.Set("a", Empty),
		"b": obj.Set("b", Bool(true)),
		"c": obj.Set("c", Long(5)),
		"d": obj.Set("d", Double(5.5)),
		"e": obj.Set("e", String("foo")),
		"f": obj.Set("f", Data([]byte{1, 2, 3}))}

	expect := map[string]expectLeaf{
		"a": expectLeaf{mytype: EMPTY},
		"b": expectLeaf{mytype: BOOL, boolVal: true},
		"c": expectLeaf{mytype: LONG, longVal: 5, doubleVal: 5},
		"d": expectLeaf{mytype: DOUBLE, longVal: 5, doubleVal: 5.5},
		"e": expectLeaf{mytype: STRING, stringVal: "foo"},
		"f": expectLeaf{mytype: DATA, dataVal: []byte{1, 2, 3}}}

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
