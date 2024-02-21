// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type NixValue struct {
	valueBase
	valid bool
}

func (n *NixValue) Valid() bool {
	return n.valid
}

func (n *NixValue) Type() Type {
	return NIX
}

// actually constants:

var ValidNix *NixValue = &NixValue{valid: true}
var InvalidNix *NixValue = &NixValue{valid: false}
