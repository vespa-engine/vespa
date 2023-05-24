// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type Slime struct {
	root Cursor
}

func NewSlime() Slime {
	return Slime{InvalidNix}
}

func (s *Slime) Get() Cursor {
	return s.root
}

func (s *Slime) SetNix() Cursor {
	s.root = ValidNix
	return s.root
}

func (s *Slime) SetBool(val bool) Cursor {
	s.root = newBoolValue(val)
	return s.root
}

func (s *Slime) SetLong(val int64) Cursor {
	s.root = newLongValue(val)
	return s.root
}

func (s *Slime) SetDouble(val float64) Cursor {
	s.root = newDoubleValue(val)
	return s.root
}

func (s *Slime) SetString(val string) Cursor {
	s.root = newStringValue(val)
	return s.root
}

func (s *Slime) SetData(val []byte) Cursor {
	s.root = newDataValue(val)
	return s.root
}

func (s *Slime) SetArray() Cursor {
	s.root = newArrayValue()
	return s.root
}

func (s *Slime) SetObject() Cursor {
	s.root = newObjectValue()
	return s.root
}

func (s *Slime) Wrap(name string) Cursor {
	top := newObjectValue()
	top.v[name] = s.root
	s.root = top
	return top
}
