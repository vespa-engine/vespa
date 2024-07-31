// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type ArrayValue struct {
	valueBase
	v []Cursor
}

func newArrayValue() *ArrayValue {
	return &ArrayValue{
		v: make([]Cursor, 0, 10),
	}
}

func (a *ArrayValue) addElement(elem Cursor) Cursor {
	a.v = append(a.v, elem)
	return elem
}

func (a *ArrayValue) Valid() bool {
	return true
}

func (a *ArrayValue) Type() Type {
	return ARRAY
}

func (a *ArrayValue) Entries() int {
	return len(a.v)
}

func (a *ArrayValue) TraverseArray(traverser ArrayTraverser) {
	for idx, value := range a.v {
		traverser.Entry(idx, value)
	}
}

func (a *ArrayValue) Entry(idx int) Inspector {
	if idx < len(a.v) {
		return a.v[idx]
	}
	return InvalidNix
}

func (a *ArrayValue) MutableEntry(idx int) Cursor {
	if idx < len(a.v) {
		return a.v[idx]
	}
	return InvalidNix
}

func (a *ArrayValue) AddNix() Inspector {
	return a.addElement(ValidNix)
}

func (a *ArrayValue) AddBool(value bool) Inspector {
	return a.addElement(newBoolValue(value))
}

func (a *ArrayValue) AddLong(value int64) Inspector {
	n := newLongValue(value)
	a.v = append(a.v, n)
	return n
}

func (a *ArrayValue) AddDouble(value float64) Inspector {
	n := newDoubleValue(value)
	a.v = append(a.v, n)
	return n
}

func (a *ArrayValue) AddString(value string) Inspector {
	n := newStringValue(value)
	a.v = append(a.v, n)
	return n
}

func (a *ArrayValue) AddData(value []byte) Inspector {
	n := newDataValue(value)
	a.v = append(a.v, n)
	return n
}

func (a *ArrayValue) AddArray() Cursor {
	n := newArrayValue()
	a.v = append(a.v, n)
	return n
}

func (a *ArrayValue) AddObject() Cursor {
	n := newObjectValue()
	a.v = append(a.v, n)
	return n
}
