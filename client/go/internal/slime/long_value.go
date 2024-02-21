// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type LongValue struct {
	valueBase
	v int64
}

func newLongValue(value int64) *LongValue {
	return &LongValue{v: value}
}

func (l *LongValue) Valid() bool {
	return true
}

func (l *LongValue) AsLong() int64 {
	return l.v
}

func (l *LongValue) AsDouble() float64 {
	return float64(l.v)
}

func (l *LongValue) Type() Type {
	return LONG
}
