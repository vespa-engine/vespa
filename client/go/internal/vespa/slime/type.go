// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

type Type byte

const (
	EMPTY Type = iota
	BOOL
	LONG
	DOUBLE
	STRING
	DATA
	ARRAY
	OBJECT
)
