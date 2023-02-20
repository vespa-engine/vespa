// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"fmt"
)

type KnownState string

// these are all the valid node states:
const (
	StateUp          KnownState = "up"
	StateDown        KnownState = "down"
	StateMaintenance KnownState = "maintenance"
	StateRetired     KnownState = "retired"
)

// verify that a string is one of the known states:
func knownState(s string) (KnownState, error) {
	alternatives := []KnownState{
		StateUp,
		StateDown,
		StateMaintenance,
		StateRetired,
	}
	for _, v := range alternatives {
		if s == string(v) {
			return v, nil
		}
	}
	return KnownState("unknown"), fmt.Errorf("<Wanted State> must be one of %v, was %s\n", alternatives, s)
}
