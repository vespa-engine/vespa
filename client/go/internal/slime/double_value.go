// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type DoubleValue struct {
	valueBase
	v float64
}

func newDoubleValue(value float64) *DoubleValue {
	return &DoubleValue{v: value}
}

func (d *DoubleValue) Valid() bool {
	return true
}

func (d *DoubleValue) AsDouble() float64 {
	return d.v
}

func (d *DoubleValue) AsLong() int64 {
	return int64(d.v)
}

func (d *DoubleValue) Type() Type {
	return DOUBLE
}
