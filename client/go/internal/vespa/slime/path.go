// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"fmt"
	"slices"
	"strings"
)

var noSelector Selector = &selectSelf{}

type Selector interface {
	Select(value Value) Value
	WouldSelectEntry(idx int) bool
	WouldSelectField(name string) bool
	String() string
}

type selectSelf struct{}

func (s *selectSelf) Select(value Value) Value {
	return value
}

func (s *selectSelf) WouldSelectEntry(idx int) bool {
	return false
}

func (s *selectSelf) WouldSelectField(name string) bool {
	return false
}

func (s *selectSelf) String() string { return "" }

type selectEntry struct {
	idx int
}

func (s *selectEntry) Select(value Value) Value {
	return value.Entry(s.idx)
}

func (s *selectEntry) WouldSelectEntry(idx int) bool {
	return idx == s.idx
}

func (s *selectEntry) WouldSelectField(name string) bool {
	return false
}

func (s *selectEntry) String() string { return fmt.Sprintf("/%d", s.idx) }

type selectField struct {
	name string
}

func (s *selectField) Select(value Value) Value {
	return value.Field(s.name)
}

func (s *selectField) WouldSelectEntry(idx int) bool {
	return false
}

func (s *selectField) WouldSelectField(name string) bool {
	return name == s.name
}

func (s *selectField) String() string { return fmt.Sprintf("/%s", s.name) }

type Path struct {
	list []Selector
}

func (p *Path) String() string {
	var sb strings.Builder
	for _, selector := range p.list {
		sb.WriteString(selector.String())
	}
	return sb.String()
}

func (p *Path) Len() int {
	return len(p.list)
}

func (p *Path) At(idx int) Selector {
	if idx < 0 {
		idx = len(p.list) + idx
	}
	if idx < 0 || idx >= len(p.list) {
		return noSelector
	}
	return p.list[idx]
}

func NewPath() *Path {
	return &Path{}
}

func (p *Path) Entry(idx int) *Path {
	p.list = append(p.list, &selectEntry{idx})
	return p
}

func (p *Path) Field(name string) *Path {
	p.list = append(p.list, &selectField{name})
	return p
}

func (p *Path) Trim(n int) *Path {
	end := len(p.list) - n
	if end < 0 {
		end = 0
	}
	p.list = p.list[:end]
	return p
}

func (p *Path) Apply(value Value) Value {
	res := value
	for _, s := range p.list {
		res = s.Select(res)
	}
	return res
}

func (p *Path) Clone() *Path {
	return &Path{slices.Clone(p.list)}
}

func Select(value Value, pred func(p *Path, v Value) bool, handle func(p *Path, v Value)) {
	path := NewPath()
	var process func(value Value)
	perEntry := func(idx int, value Value) {
		path.Entry(idx)
		process(value)
		path.Trim(1)
	}
	perField := func(name string, value Value) {
		path.Field(name)
		process(value)
		path.Trim(1)
	}
	process = func(value Value) {
		if pred(path, value) {
			handle(path, value)
			return
		}
		value.EachEntry(perEntry)
		value.EachField(perField)
	}
	process(value)
}

func Find(value Value, pred func(path *Path, value Value) bool) []*Path {
	var result []*Path
	Select(value, pred, func(p *Path, v Value) {
		result = append(result, p.Clone())
	})
	return result
}
