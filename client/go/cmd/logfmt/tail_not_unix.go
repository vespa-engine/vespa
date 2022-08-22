// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: mpolden

//go:build windows

package logfmt

import (
	"fmt"
)

func FollowFile(fn string) (Tail, error) {
	return nil, fmt.Errorf("tail is not supported on this platform")
}
