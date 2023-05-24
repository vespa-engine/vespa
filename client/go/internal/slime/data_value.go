// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type DataValue struct {
	valueBase
	v []byte
}

func newDataValue(value []byte) *DataValue {
	return &DataValue{v: value}
}

func (d *DataValue) Valid() bool {
	return true
}

func (d *DataValue) AsData() []byte {
	return d.v
}

func (d *DataValue) Type() Type {
	return DATA
}
