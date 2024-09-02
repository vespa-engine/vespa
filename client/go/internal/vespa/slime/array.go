// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

type arrayValue struct {
	emptyValue
	value []Value
}

func Array() *arrayValue       { return &arrayValue{} }
func (*arrayValue) Type() Type { return ARRAY }

func (arr *arrayValue) NumEntries() int { return len(arr.value) }
func (arr *arrayValue) Entry(index int) Value {
	if index < len(arr.value) {
		return arr.value[index]
	}
	return Invalid
}
func (arr *arrayValue) EachEntry(f func(index int, value Value)) {
	for i, x := range arr.value {
		f(i, x)
	}
}

func (arr *arrayValue) Add(value Value) Value {
	arr.value = append(arr.value, value)
	return value
}
