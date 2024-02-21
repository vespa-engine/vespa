// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type BoolValue struct {
	valueBase
	v bool
}

func newBoolValue(value bool) *BoolValue {
	return &BoolValue{v: value}
}

func (b *BoolValue) Valid() bool {
	return true
}

func (b *BoolValue) AsBool() bool {
	return b.v
}

func (b *BoolValue) Type() Type {
	return BOOL
}
