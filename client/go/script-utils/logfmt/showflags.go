// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"strings"
)

// handle CLI flags for which fields to show when formatting a line

type flagValueForShow struct {
	shown   map[string]bool
	changed bool
}

func defaultShowFlags() map[string]bool {
	return map[string]bool{
		"time":      true,
		"fmttime":   true,
		"msecs":     true,
		"usecs":     false,
		"host":      false,
		"level":     true,
		"pid":       false,
		"service":   true,
		"component": true,
		"message":   true,
	}
}

func (v *flagValueForShow) Type() string {
	return "show flags"
}

func (v *flagValueForShow) String() string {
	var buf strings.Builder
	flagNames := []string{
		"time",
		"fmttime",
		"msecs",
		"usecs",
		"host",
		"level",
		"pid",
		"service",
		"component",
		"message",
	}
	for _, flag := range flagNames {
		if v.shown[flag] {
			buf.WriteString(" +")
		} else {
			buf.WriteString(" -")
		}
		buf.WriteString(flag)
	}
	return buf.String()
}

func (v *flagValueForShow) flags() map[string]bool {
	return v.shown
}

func (v *flagValueForShow) name() string {
	return "show"
}

func (v *flagValueForShow) unchanged() bool {
	return !v.changed
}

func (v *flagValueForShow) Set(val string) error {
	return applyPlusMinus(val, v)
}
