// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import "errors"

var (
	Invalid Value = ErrorMsg("invalid value")
)

type errorValue struct {
	emptyValue
	err error
}

func ErrorMsg(msg string) Value {
	return &errorValue{err: errors.New(msg)}
}

func Error(err error) Value {
	return &errorValue{err: err}
}

func AsError(value Value) error {
	ev, ok := value.(*errorValue)
	if ok {
		return ev.err
	}
	return nil
}

func (*errorValue) Valid() bool { return false }
