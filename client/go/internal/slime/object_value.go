// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

import (
	"sort"
)

type ObjectValue struct {
	valueBase
	v map[string]Cursor
}

func newObjectValue() *ObjectValue {
	return &ObjectValue{
		v: make(map[string]Cursor),
	}
}

func (o *ObjectValue) addElement(name string, elem Cursor) Cursor {
	o.v[name] = elem
	return elem
}

func (o *ObjectValue) Valid() bool {
	return true
}

func (o *ObjectValue) Type() Type {
	return OBJECT
}

func (o *ObjectValue) Fields() int {
	return len(o.v)
}

func (o *ObjectValue) TraverseObject(traverser ObjectTraverser) {
	keys := make([]string, 0, len(o.v))
	for k := range o.v {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, name := range keys {
		value := o.v[name]
		traverser.Field(name, value)
	}
}

func (o *ObjectValue) Field(name string) Inspector {
	if r, ok := o.v[name]; ok {
		return r
	}
	return InvalidNix
}

func (o *ObjectValue) MutableField(name string) Cursor {
	if r, ok := o.v[name]; ok {
		return r
	}
	return InvalidNix
}

func (o *ObjectValue) SetNix(name string) Inspector {
	return o.addElement(name, ValidNix)
}

func (o *ObjectValue) SetBool(name string, value bool) Inspector {
	return o.addElement(name, newBoolValue(value))
}

func (o *ObjectValue) SetLong(name string, value int64) Inspector {
	return o.addElement(name, newLongValue(value))
}

func (o *ObjectValue) SetDouble(name string, value float64) Inspector {
	return o.addElement(name, newDoubleValue(value))
}

func (o *ObjectValue) SetString(name string, value string) Inspector {
	return o.addElement(name, newStringValue(value))
}

func (o *ObjectValue) SetData(name string, value []byte) Inspector {
	return o.addElement(name, newDataValue(value))
}

func (o *ObjectValue) SetArray(name string) Cursor {
	return o.addElement(name, newArrayValue())
}

func (o *ObjectValue) SetObject(name string) Cursor {
	return o.addElement(name, newObjectValue())
}
