// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

type expectLeaf struct {
	invalid   bool
	mytype    Type
	boolVal   bool
	longVal   int64
	doubleVal float64
	stringVal string
	dataVal   []byte
}

func checkLeaf(t *testing.T, value Value, expect expectLeaf) {
	if expect.dataVal == nil {
		expect.dataVal = emptyBytes
	}
	assert.Equal(t, value.Valid(), !expect.invalid)
	if expect.invalid {
		assert.Equal(t, AsError(value).Error(), "invalid value")
	} else {
		assert.Equal(t, AsError(value), nil)
	}
	assert.Equal(t, value.Type(), expect.mytype)
	assert.Equal(t, value.AsBool(), expect.boolVal)
	assert.Equal(t, value.AsLong(), expect.longVal)
	assert.Equal(t, value.AsDouble(), expect.doubleVal)
	assert.Equal(t, value.AsString(), expect.stringVal)
	assert.Equal(t, value.AsData(), expect.dataVal)
}

func TestError(t *testing.T) {
	err := ErrorMsg("test error")
	assert.False(t, err.Valid())
	assert.Equal(t, AsError(err).Error(), "test error")
}

func TestEmpty(t *testing.T) {
	checkLeaf(t, Empty, expectLeaf{})
	checkLeaf(t, Invalid, expectLeaf{invalid: true})
}

func TestBool(t *testing.T) {
	checkLeaf(t, Bool(false), expectLeaf{mytype: BOOL})
	checkLeaf(t, Bool(true), expectLeaf{mytype: BOOL, boolVal: true})
}

func TestLong(t *testing.T) {
	checkLeaf(t, Long(0), expectLeaf{mytype: LONG})
	checkLeaf(t, Long(5), expectLeaf{mytype: LONG, longVal: 5, doubleVal: 5})
	checkLeaf(t, Long(7), expectLeaf{mytype: LONG, longVal: 7, doubleVal: 7})
}

func TestDouble(t *testing.T) {
	checkLeaf(t, Double(0.0), expectLeaf{mytype: DOUBLE})
	checkLeaf(t, Double(5.0), expectLeaf{mytype: DOUBLE, longVal: 5, doubleVal: 5.0})
	checkLeaf(t, Double(7.5), expectLeaf{mytype: DOUBLE, longVal: 7, doubleVal: 7.5})
}

func TestString(t *testing.T) {
	checkLeaf(t, String(""), expectLeaf{mytype: STRING})
	checkLeaf(t, String("foo"), expectLeaf{mytype: STRING, stringVal: "foo"})
	checkLeaf(t, String("bar"), expectLeaf{mytype: STRING, stringVal: "bar"})
}

func TestData(t *testing.T) {
	checkLeaf(t, Data(emptyBytes), expectLeaf{mytype: DATA})
	checkLeaf(t, Data([]byte{1, 2, 3}), expectLeaf{mytype: DATA, dataVal: []byte{1, 2, 3}})
	checkLeaf(t, Data([]byte{5, 6}), expectLeaf{mytype: DATA, dataVal: []byte{5, 6}})
}
