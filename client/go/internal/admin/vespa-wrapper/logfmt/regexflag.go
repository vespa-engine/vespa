// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"regexp"
)

// optional regular expression filter, as a CLI flag

type regexFlag struct {
	regex *regexp.Regexp
}

func (re regexFlag) unmatched(s string) bool {
	if re.regex == nil {
		return false
	}
	return re.regex.FindStringIndex(s) == nil
}

func (v *regexFlag) Type() string {
	return "regular expression"
}

func (v *regexFlag) String() string {
	if v.regex == nil {
		return "<none>"
	}
	return v.regex.String()
}

func (v *regexFlag) Set(val string) (r error) {
	v.regex, r = regexp.Compile(val)
	return
}
