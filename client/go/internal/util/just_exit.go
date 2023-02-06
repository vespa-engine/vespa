// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

type JustExitError struct {
	err error
	msg string
}

func (j *JustExitError) String() string {
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

func (j *JustExitError) Error() string {
	return j.String()
}

func JustExitMsg(message string) {
	trace.Trace("just exit with message")
	j := JustExitError{
		err: nil,
		msg: message,
	}
	panic(&j)
}

func JustExitWith(e error) {
	trace.Trace("just exit with error")
	j := JustExitError{
		err: e,
		msg: "",
	}
	panic(&j)
}
