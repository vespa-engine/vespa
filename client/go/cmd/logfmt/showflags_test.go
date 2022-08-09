// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"strings"
	"testing"
)

func TestShowFlags(t *testing.T) {
	none := " -time -fmttime -msecs -usecs -host -level -pid -service -component -message"
	var flag flagValueForShow
	assert.Equal(t, none, flag.String(), "unset flag displays as '%s', expected '%s'", flag.String(), none)
	assert.Equal(t, "show flags", flag.Type())
	check := func(expected string, texts ...string) {
		var target flagValueForShow
		// target.levels = defaultLevelFlags()
		target.shown = defaultShowFlags()
		for _, text := range texts {
			err := target.Set(text)
			require.Nil(t, err, "unexpected error with show flags Set('%s'): %v", text, err)
		}
		assert.Equal(t, expected, target.String())
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
				require.Equal(t, expectErr, err.Error())
				return
			}
		}
		t.Logf("Did not get expected error [%s] from %s", expectErr, strings.Join(texts, " "))
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
