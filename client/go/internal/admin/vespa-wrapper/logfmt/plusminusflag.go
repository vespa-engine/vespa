// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"fmt"
	"strings"
)

// common code for showFlags and levelFlags

type plusMinusFlag interface {
	flags() map[string]bool
	name() string
	unchanged() bool
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

func applyPlusMinus(val string, target plusMinusFlag) error {
	minus := strings.HasPrefix(val, "-")
	plus := strings.HasPrefix(val, "+")
	val = strings.ReplaceAll(val, "-", ",-")
	val = strings.ReplaceAll(val, "+", ",+")
	if target.unchanged() {
		// user wants to reset flags?
		if minus == false && plus == false {
			for k, _ := range target.flags() {
				target.flags()[k] = false
			}
		}
	}
	changeTo := !minus
	for _, k := range strings.Split(val, ",") {
		if suppress, minus := trimPrefix(k, "-"); minus {
			k = suppress
			changeTo = false
		}
		if surface, plus := trimPrefix(k, "+"); plus {
			k = surface
			changeTo = true
		}
		if k == "" {
			continue
		}
		if k == "all" {
			for k, _ := range target.flags() {
				target.flags()[k] = changeTo
			}
		} else if _, ok := target.flags()[k]; !ok {
			return fmt.Errorf("not a valid %s flag: '%s'", target.name(), k)
		} else {
			target.flags()[k] = changeTo
		}
	}
	return nil
}
