// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type StringValue struct {
	valueBase
	v string
}

func newStringValue(value string) *StringValue {
	return &StringValue{v: value}
}

func (s *StringValue) Valid() bool {
	return true
}

func (s *StringValue) AsString() string {
	return s.v
}

func (s *StringValue) AsData() []byte {
	return []byte(s.v)
}

func (s *StringValue) Type() Type {
	return STRING
}
