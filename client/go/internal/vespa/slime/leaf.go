// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

type boolValue struct {
	emptyValue
	value bool
}

func Bool(v bool) Value           { return &boolValue{value: v} }
func (*boolValue) Type() Type     { return BOOL }
func (v *boolValue) AsBool() bool { return v.value }

type longValue struct {
	emptyValue
	value int64
}

func Long(v int64) Value               { return &longValue{value: v} }
func (*longValue) Type() Type          { return LONG }
func (v *longValue) AsLong() int64     { return v.value }
func (v *longValue) AsDouble() float64 { return float64(v.value) }

type doubleValue struct {
	emptyValue
	value float64
}

func Double(v float64) Value             { return &doubleValue{value: v} }
func (*doubleValue) Type() Type          { return DOUBLE }
func (v *doubleValue) AsLong() int64     { return int64(v.value) }
func (v *doubleValue) AsDouble() float64 { return v.value }

type stringValue struct {
	emptyValue
	value string
}

func String(v string) Value             { return &stringValue{value: v} }
func (*stringValue) Type() Type         { return STRING }
func (v *stringValue) AsString() string { return v.value }

type dataValue struct {
	emptyValue
	value []byte
}

func Data(v []byte) Value           { return &dataValue{value: v} }
func (*dataValue) Type() Type       { return DATA }
func (v *dataValue) AsData() []byte { return v.value }
