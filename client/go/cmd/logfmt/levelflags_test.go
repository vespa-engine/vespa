// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"strings"
	"testing"
)

func TestLevelFlags(t *testing.T) {
	none := " -fatal -error -warning -info -config -event -debug -spam"

	var flag flagValueForLevel
	if flag.String() != none {
		t.Logf("unset flag displays as '%s', expected '%s'", flag.String(), none)
		t.Fail()
	}
	if flag.Type() != "level flags" {
		t.Logf("flag type was '%s'", flag.Type())
		t.Fail()
	}
	check := func(expected string, texts ...string) {
		var target flagValueForLevel
		// target.levels = defaultLevelFlags()
		target.levels = defaultLevelFlags()
		for _, text := range texts {
			err := target.Set(text)
			if err != nil {
				t.Fatalf("unexpected error with level flags Set('%s'): %v", text, err)
			}
		}
		got := target.String()
		if got != expected {
			t.Logf("expected flags [%s] but got: [%s]", expected, got)
			t.Fail()
		}
	}
	check(" +fatal +error +warning +info -config -event -debug -spam")
	check(" -fatal -error -warning -info -config -event -debug -spam", "-all")
	check(" +fatal +error +warning +info +config +event +debug +spam", "all")
	check(" +fatal +error +warning +info +config +event +debug +spam", "+all")
	check(" -fatal -error -warning -info -config -event +debug -spam", "debug")
	check(" +fatal +error +warning +info +config +event +debug -spam", "all-spam")
	check(" +fatal +error +warning +info +config +event +debug -spam", "all", "-spam")
	check(" +fatal +error +warning -info -config +event -debug -spam", "+event", "-info")
	check(" +fatal +error -warning -info -config +event -debug -spam", "+event,-info,-warning,config")
	check(" +fatal +error -warning -info +config +event -debug -spam", "+event,-info,-warning,+config")
	check(" +fatal +error -warning -info +config +event -debug -spam", "+event,-info", "-warning,+config")
	check(" -fatal -error -warning -info +config -event -debug -spam", "+event", "-info", "-warning", "config")
	check = func(expectErr string, texts ...string) {
		var target flagValueForLevel
		target.levels = defaultLevelFlags()
		for _, text := range texts {
			err := target.Set(text)
			if err != nil {
				if err.Error() == expectErr {
					return
				}
				t.Fatalf("expected error [%s] with level flags Set('%s'), but got [%v]", expectErr, text, err)
			}
		}
		t.Logf("Did not get expected error '%s' from %s", expectErr, strings.Join(texts, ","))
		t.Fail()
	}
	check("not a valid level flag: 'foo'", "foo")
	check("not a valid level flag: 'foo'", "event,foo,config")
	check("not a valid level flag: 'foo'", "-event,-foo,-config")
	check("not a valid level flag: 'foo'", "+event,+foo,+config")
	check("not a valid level flag: 'foo'", "event", "foo", "config")
	check("not a valid level flag: 'foo'", "-event", "-foo", "-config")
	check("not a valid level flag: 'foo'", "+event", "+foo", "+config")
}
