// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type valueBase struct {
}

func (v valueBase) Entries() int                        { return 0 }
func (v valueBase) Fields() int                         { return 0 }
func (v valueBase) AsBool() bool                        { return false }
func (v valueBase) AsLong() int64                       { return 0 }
func (v valueBase) AsDouble() float64                   { return 0.0 }
func (v valueBase) AsString() string                    { return "" }
func (v valueBase) AsData() []byte                      { return emptyByteSlice }
func (v valueBase) TraverseArray(ArrayTraverser)        { return }
func (v valueBase) TraverseObject(ObjectTraverser)      { return }
func (v valueBase) Entry(int) Inspector                 { return InvalidNix }
func (v valueBase) Field(string) Inspector              { return InvalidNix }
func (v valueBase) MutableEntry(int) Cursor             { return InvalidNix }
func (v valueBase) MutableField(string) Cursor          { return InvalidNix }
func (v valueBase) AddNix() Inspector                   { return InvalidNix }
func (v valueBase) AddBool(bool) Inspector              { return InvalidNix }
func (v valueBase) AddLong(int64) Inspector             { return InvalidNix }
func (v valueBase) AddDouble(float64) Inspector         { return InvalidNix }
func (v valueBase) AddString(string) Inspector          { return InvalidNix }
func (v valueBase) AddData([]byte) Inspector            { return InvalidNix }
func (v valueBase) AddArray() Cursor                    { return InvalidNix }
func (v valueBase) AddObject() Cursor                   { return InvalidNix }
func (v valueBase) SetNix(string) Inspector             { return InvalidNix }
func (v valueBase) SetBool(string, bool) Inspector      { return InvalidNix }
func (v valueBase) SetLong(string, int64) Inspector     { return InvalidNix }
func (v valueBase) SetDouble(string, float64) Inspector { return InvalidNix }
func (v valueBase) SetString(string, string) Inspector  { return InvalidNix }
func (v valueBase) SetData(string, []byte) Inspector    { return InvalidNix }
func (v valueBase) SetArray(string) Cursor              { return InvalidNix }
func (v valueBase) SetObject(string) Cursor             { return InvalidNix }
