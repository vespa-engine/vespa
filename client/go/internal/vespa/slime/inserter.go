// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

type Inserter interface {
	Insert(value Value) Value
}

type myInserter func(value Value) Value

func (f myInserter) Insert(value Value) Value {
	return f(value)
}

func InsertRoot(root *Value) Inserter {
	return myInserter(func(value Value) Value {
		*root = value
		return value
	})
}

func InsertEntry(arr Value) Inserter {
	return myInserter(func(value Value) Value {
		return arr.Add(value)
	})
}

func InsertField(obj Value, name string) Inserter {
	return myInserter(func(value Value) Value {
		return obj.Set(name, value)
	})
}
