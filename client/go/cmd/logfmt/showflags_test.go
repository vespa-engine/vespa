// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"strings"
	"testing"
)

func TestShowFlags(t *testing.T) {
	none := " -time -fmttime -msecs -usecs -host -level -pid -service -component -message"
	var flag flagValueForShow
	if flag.String() != none {
		t.Logf("unset flag displays as '%s', expected '%s'", flag.String(), none)
		t.Fail()
	}
	if flag.Type() != "show flags" {
		t.Logf("flag type was '%s'", flag.Type())
		t.Fail()
	}
	check := func(expected string, texts ...string) {
		var target flagValueForShow
		// target.levels = defaultLevelFlags()
		target.shown = defaultShowFlags()
		for _, text := range texts {
			err := target.Set(text)
			if err != nil {
				t.Logf("unexpected error with show flags Set('%s'): %v", text, err)
				t.FailNow()
			}
		}
		got := target.String()
		if got != expected {
			t.Logf("expected flags [%s] but got: [%s]", expected, got)
			t.Fail()
		}
	}
	check(" +time +fmttime +msecs -usecs -host +level -pid +service +component +message")
	check(" -time -fmttime -msecs -usecs -host -level -pid -service -component -message", "-all")
	check(" +time +fmttime +msecs +usecs +host +level +pid +service +component +message", "all")
	check(" +time +fmttime +msecs +usecs +host +level +pid +service +component +message", "+all")
	check(" -time -fmttime -msecs -usecs -host -level +pid -service -component -message", "pid")
	check(" +time +fmttime +msecs +usecs -host +level +pid +service +component +message", "all-host")
	check(" +time +fmttime +msecs +usecs -host +level +pid +service +component +message", "all", "-host")
	check(" +time +fmttime -msecs -usecs -host +level +pid +service +component +message", "+pid", "-msecs")
	check(" +time -fmttime +msecs -usecs +host +level -pid -service +component +message", "+host,-fmttime,-service,pid")
	check(" +time -fmttime +msecs -usecs +host +level +pid -service +component +message", "+host,-fmttime,-service,+pid")
	check(" +time -fmttime +msecs -usecs +host +level +pid -service +component +message", "+host,-fmttime", "-service,+pid")
	check(" -time -fmttime -msecs -usecs -host -level +pid -service -component -message", "+host", "-fmttime", "-service", "pid")
	check = func(expectErr string, texts ...string) {
		var target flagValueForShow
		target.shown = defaultShowFlags()
		for _, text := range texts {
			err := target.Set(text)
			if err != nil {
				if err.Error() == expectErr {
					return
				}
				t.Logf("expected error [%s] with show flags Set('%s'), but got [%v]", expectErr, text, err)
				t.FailNow()
			}
		}
		t.Logf("Did not get expected error '%s' from %s", expectErr, strings.Join(texts, ","))
		t.Fail()
	}
	check("not a valid show flag: 'foo'", "foo")
	check("not a valid show flag: 'foo'", "level,foo,message")
	check("not a valid show flag: 'foo'", "-level,-foo,-message")
	check("not a valid show flag: 'foo'", "+level,+foo,+message")
	check("not a valid show flag: 'foo'", "level", "foo", "message")
	check("not a valid show flag: 'foo'", "-level", "-foo", "-message")
	check("not a valid show flag: 'foo'", "+level", "+foo", "+message")
}
