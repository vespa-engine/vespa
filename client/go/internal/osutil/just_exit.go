// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package osutil

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

type ExitError struct {
	err error
	msg string
}

func (j *ExitError) String() string {
	if j.err != nil {
		if j.msg == "" {
			return j.err.Error()
		}
		return fmt.Sprintf("%s: %s", j.msg, j.err.Error())
	}
	if j.msg == "" {
		panic(j)
	}
	return j.msg
}

func (j *ExitError) Error() string {
	return j.String()
}

func ExitMsg(message string) {
	trace.Trace("just exit with message")
	j := ExitError{
		err: nil,
		msg: message,
	}
	panic(&j)
}

func ExitErr(e error) {
	trace.Trace("just exit with error")
	j := ExitError{
		err: e,
		msg: "",
	}
	panic(&j)
}
