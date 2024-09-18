// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

var (
	emptyBytes []byte = make([]byte, 0)
	Empty      Value  = &emptyValue{}
)

type Value interface {
	Valid() bool
	Type() Type
	AsBool() bool
	AsLong() int64
	AsDouble() float64
	AsString() string
	AsData() []byte
	NumEntries() int
	Entry(index int) Value
	EachEntry(func(index int, value Value))
	NumFields() int
	Field(name string) Value
	EachField(func(name string, value Value))
	Add(value Value) Value
	Set(name string, value Value) Value
}

type emptyValue struct{}

func (*emptyValue) Valid() bool                              { return true }
func (*emptyValue) Type() Type                               { return EMPTY }
func (*emptyValue) AsBool() bool                             { return false }
func (*emptyValue) AsLong() int64                            { return 0 }
func (*emptyValue) AsDouble() float64                        { return 0 }
func (*emptyValue) AsString() string                         { return "" }
func (*emptyValue) AsData() []byte                           { return emptyBytes }
func (*emptyValue) NumEntries() int                          { return 0 }
func (*emptyValue) Entry(index int) Value                    { return Invalid }
func (*emptyValue) EachEntry(func(index int, value Value))   {}
func (*emptyValue) NumFields() int                           { return 0 }
func (*emptyValue) Field(name string) Value                  { return Invalid }
func (*emptyValue) EachField(func(name string, value Value)) {}
func (*emptyValue) Add(value Value) Value                    { return Invalid }
func (*emptyValue) Set(name string, value Value) Value       { return Invalid }

func ToString(value Value) string { return ToJson(value, false) }
