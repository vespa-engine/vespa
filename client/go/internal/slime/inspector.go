// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type Inspector interface {
	Valid() bool
	Type() Type
	Entries() int // for arrays only
	Fields() int  // for objects only
	AsBool() bool
	AsLong() int64
	AsDouble() float64
	AsString() string
	AsData() []byte
	TraverseArray(ArrayTraverser)
	TraverseObject(ObjectTraverser)
	Entry(int) Inspector
	Field(string) Inspector
}
