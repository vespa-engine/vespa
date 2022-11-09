// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"strings"
)

type Spec struct {
	Program              string
	Args                 []string
	BaseName             string
	Env                  map[string]string
	numaSocket           int
	shouldUseCallgrind   bool
	shouldUseValgrind    bool
	shouldUseNumaCtl     bool
	shouldUseVespaMalloc bool
	vespaMallocPreload   string
}

func NewSpec(argv []string) *Spec {
	progName := argv[0]
	p := Spec{
		Program:    progName,
		Args:       argv,
		BaseName:   baseNameOf(progName),
		Env:        make(map[string]string),
		numaSocket: -1,
	}
	return &p
}

func baseNameOf(s string) string {
	idx := strings.LastIndex(s, "/")
	idx++
	return s[idx:]
}
