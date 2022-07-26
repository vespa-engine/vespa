// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-logfmt command
// Author: arnej

package main

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/cmd/logfmt"
)

func NewLogfmtCmd() *cobra.Command {
	var (
		curOptions logfmt.Options = logfmt.NewOptions()
	)
	cmd := &cobra.Command{
		Use:   "vespa-logfmt",
		Short: "convert vespa.log to human-readable format",
		Long: `vespa-logfmt takes input in the internal vespa format
and converts it to something human-readable`,
		Run: func(cmd *cobra.Command, args []string) {
			logfmt.RunLogfmt(&curOptions, args)
		},
		Args: cobra.MaximumNArgs(1),
	}
	cmd.Flags().VarP(&curOptions.ShowLevels, "level", "l", "turn levels on/off\n")
	cmd.Flags().VarP(&curOptions.ShowFields, "show", "s", "turn fields shown on/off\n")
	cmd.Flags().Var(&curOptions.ComponentFilter, "component", "select components by regexp")
	cmd.Flags().Var(&curOptions.MessageFilter, "message", "select messages by regexp")
	cmd.Flags().BoolVar(&curOptions.OnlyInternal, "internal", false, "select only internal components")
	cmd.Flags().BoolVar(&curOptions.TruncateService, "truncateservice", false, "truncate service name")
	cmd.Flags().BoolVarP(&curOptions.FollowTail, "follow", "f", false, "follow logfile with tail -f")
	cmd.Flags().BoolVarP(&curOptions.DequoteNewlines, "nldequote", "N", false, "dequote newlines embedded in message")
	cmd.Flags().BoolVarP(&curOptions.TruncateComponent, "truncatecomponent", "t", false, "truncate component name")
	cmd.Flags().StringVarP(&curOptions.OnlyHostname, "host", "H", "", "select only one host")
	cmd.Flags().StringVarP(&curOptions.OnlyPid, "pid", "p", "", "select only one process ID")
	cmd.Flags().StringVarP(&curOptions.OnlyService, "service", "S", "", "select only one service")
	return cmd
}
