// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"fmt"
	"strings"
)

type flagValueForShow struct {
	shown   map[string]bool
	changed bool
}

func trimPrefix(value, prefix string) (newValue string, hadPrefix bool) {
	hadPrefix = strings.HasPrefix(value, prefix)
	if hadPrefix {
		newValue = strings.TrimPrefix(value, prefix)
	} else {
		newValue = value
	}
	return
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
	mv := v.shown
	var buf strings.Builder
	buf.WriteString("show flags:")
	for flag, active := range mv {
		if active {
			buf.WriteString(" +")
		} else {
			buf.WriteString(" -")
		}
		buf.WriteString(flag)
	}
	return buf.String()
}

func (v *flagValueForShow) Set(val string) error {
	minus := strings.HasPrefix(val, "-")
	plus := strings.HasPrefix(val, "+")
	val = strings.ReplaceAll(val, "-", ",-")
	val = strings.ReplaceAll(val, "+", ",+")
	if !v.changed {
		if minus == false && plus == false {
			for k, _ := range v.shown {
				v.shown[k] = false
			}
		}
	}
	toShow := !minus
	for _, k := range strings.Split(val, ",") {
		if suppress, minus := trimPrefix(k, "-"); minus {
			k = suppress
			toShow = false
		}
		if surface, plus := trimPrefix(k, "+"); plus {
			k = surface
			toShow = true
		}
		if k == "" {
			continue
		}
		if k == "all" {
			for k, _ := range v.shown {
				v.shown[k] = toShow
			}
		} else if _, ok := v.shown[k]; !ok {
			return fmt.Errorf("not a valid show flag: '%s'", k)
		} else {
			v.shown[k] = toShow
		}
	}
	return nil
}
