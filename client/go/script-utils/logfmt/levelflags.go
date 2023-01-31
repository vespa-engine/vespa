// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"strings"
)

// handle CLI flags for log level filtering

type flagValueForLevel struct {
	levels  map[string]bool
	changed bool
}

func defaultLevelFlags() map[string]bool {
	return map[string]bool{
		"fatal":   true,
		"error":   true,
		"warning": true,
		"info":    true,
		"config":  false,
		"event":   false,
		"debug":   false,
		"spam":    false,
	}
}

func (v *flagValueForLevel) Type() string {
	return "level flags"
}

func (v *flagValueForLevel) String() string {
	var buf strings.Builder
	flagNames := []string{
		"fatal",
		"error",
		"warning",
		"info",
		"config",
		"event",
		"debug",
		"spam",
	}
	for _, flag := range flagNames {
		if v.levels[flag] {
			buf.WriteString(" +")
		} else {
			buf.WriteString(" -")
		}
		buf.WriteString(flag)
	}
	return buf.String()
}

func (v *flagValueForLevel) flags() map[string]bool {
	return v.levels
}

func (v *flagValueForLevel) name() string {
	return "level"
}

func (v *flagValueForLevel) unchanged() bool {
	return !v.changed
}

func (v *flagValueForLevel) Set(val string) error {
	return applyPlusMinus(val, v)
}
