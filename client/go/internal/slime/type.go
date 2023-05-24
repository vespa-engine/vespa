// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type Type byte

const (
	NIX Type = iota
	BOOL
	LONG
	DOUBLE
	STRING
	DATA
	ARRAY
	OBJECT
)
