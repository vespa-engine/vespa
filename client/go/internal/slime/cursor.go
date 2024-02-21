// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type Cursor interface {
	Inspector
	MutableEntry(int) Cursor
	MutableField(string) Cursor
	// for arrays only:
	AddNix() Inspector
	AddBool(bool) Inspector
	AddLong(int64) Inspector
	AddDouble(float64) Inspector
	AddString(string) Inspector
	AddData([]byte) Inspector
	AddArray() Cursor
	AddObject() Cursor
	// for objects only:
	SetNix(string) Inspector
	SetBool(string, bool) Inspector
	SetLong(string, int64) Inspector
	SetDouble(string, float64) Inspector
	SetString(string, string) Inspector
	SetData(string, []byte) Inspector
	SetArray(string) Cursor
	SetObject(string) Cursor
}
