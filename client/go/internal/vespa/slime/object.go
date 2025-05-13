// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

type objectValue struct {
	emptyValue
	value map[string]Value
}

func Object() Value             { return &objectValue{value: make(map[string]Value)} }
func (*objectValue) Type() Type { return OBJECT }

func (obj *objectValue) NumFields() int { return len(obj.value) }
func (obj *objectValue) Field(name string) Value {
	value, found := obj.value[name]
	if found {
		return value
	}
	return Invalid
}

func (obj *objectValue) EachField(f func(name string, value Value)) {
	for n, x := range obj.value {
		f(n, x)
	}
}

func (obj *objectValue) Set(name string, value Value) Value {
	obj.value[name] = value
	return value
}

func (obj *objectValue) Emit(dst DataSink) {
	dst.Object()
	for key, value := range obj.value {
		dst.Key(key)
		value.Emit(dst)
	}
	dst.End()
}

func MakeObject(fill func(obj Value)) Value {
	obj := Object()
	fill(obj)
	return obj
}
