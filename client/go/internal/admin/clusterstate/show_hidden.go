// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"strconv"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

// handle CLI flag --show-hidden

type showHiddenFlag struct {
	showHidden bool
	cmd        *cobra.Command
}

func (v *showHiddenFlag) Type() string {
	return ""
}

func (v *showHiddenFlag) String() string {
	return strconv.FormatBool(v.showHidden)
}

func (v *showHiddenFlag) Set(val string) error {
	b, err := strconv.ParseBool(val)
	v.showHidden = b
	v.cmd.Flags().VisitAll(func(f *pflag.Flag) { f.Hidden = false })
	return err
}

func (v *showHiddenFlag) IsBoolFlag() bool { return true }
