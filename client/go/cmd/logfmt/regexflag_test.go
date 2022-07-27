// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"testing"
)

func checkMatch(t *testing.T, re string, flag *regexFlag, texts ...string) {
	err := flag.Set(re)
	if err != nil {
		t.Logf("unexpected error with flag.Set('%s'): %v", re, err)
		t.FailNow()
	}
	if flag.String() != re {
		t.Logf("set flag displays as '%s', expected '%s'", flag.String(), re)
		t.Fail()
	}
	for _, text := range texts {
		if flag.unmatched(text) {
			t.Logf("flag '%s' claims a non-match for '%s'", flag.String(), text)
			t.Fail()
		}
	}
}

func checkUnmatch(t *testing.T, re string, flag *regexFlag, texts ...string) {
	err := flag.Set(re)
	if err != nil {
		t.Logf("unexpected error with flag.Set('%s'): %v", re, err)
		t.FailNow()
	}
	if flag.String() != re {
		t.Logf("set flag displays as '%s', expected '%s'", flag.String(), re)
		t.Fail()
	}
	for _, text := range texts {
		if !flag.unmatched(text) {
			t.Logf("flag '%s' should claim a non-match for '%s'", flag.String(), text)
			t.Fail()
		}
	}
}

func TestRegexFlag(t *testing.T) {
	var flag regexFlag
	if flag.String() != "<none>" {
		t.Logf("unset flag displays as '%s', expected <none>", flag.String())
		t.Fail()
	}
	if flag.Type() != "regular expression" {
		t.Logf("flag type was '%s'", flag.Type())
		t.Fail()
	}
	if flag.unmatched("foobar") {
		t.Log("unset flag claims a non-match")
		t.Fail()
	}
	checkMatch(t, "foo.*bar", &flag, "foobar", "foo bar", "x foobar y", "xfoobary", "xfooybarz")
	checkUnmatch(t, "foo.*bar", &flag, "Foobar", "foo Bar", "fxoobar", "whatever")
}
