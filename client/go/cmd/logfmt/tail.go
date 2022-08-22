// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: mpolden

package logfmt

type Line struct {
	Text string
}

type Tail interface {
	Lines() chan Line
}
