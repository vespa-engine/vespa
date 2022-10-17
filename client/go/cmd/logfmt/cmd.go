// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-logfmt command
// Author: arnej

package logfmt

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/build"
)

func NewLogfmtCmd() *cobra.Command {
	var (
		curOptions Options = NewOptions()
	)
	cmd := &cobra.Command{
		Use:   "vespa-logfmt",
		Short: "convert vespa.log to human-readable format",
		Long: `vespa-logfmt takes input in the internal vespa format
and converts it to something human-readable`,
		Version: build.Version,
		Run: func(cmd *cobra.Command, args []string) {
			RunLogfmt(&curOptions, args)
		},
	}
	cmd.Flags().VarP(&curOptions.ShowLevels, "level", "l", "turn levels on/off\n")
	cmd.Flags().VarP(&curOptions.ShowFields, "show", "s", "turn fields shown on/off\n")
	cmd.Flags().VarP(&curOptions.ComponentFilter, "component", "c", "select components by regexp")
	cmd.Flags().VarP(&curOptions.MessageFilter, "message", "m", "select messages by regexp")
	cmd.Flags().BoolVarP(&curOptions.OnlyInternal, "internal", "i", false, "select only internal components")
	cmd.Flags().BoolVar(&curOptions.TruncateService, "truncateservice", false, "truncate service name")
	cmd.Flags().BoolVar(&curOptions.TruncateService, "ts", false, "")
	cmd.Flags().BoolVarP(&curOptions.FollowTail, "follow", "f", false, "follow logfile with tail -f")
	cmd.Flags().BoolVarP(&curOptions.DequoteNewlines, "nldequote", "N", false, "dequote newlines embedded in message")
	cmd.Flags().BoolVarP(&curOptions.DequoteNewlines, "dequotenewlines", "n", false, "dequote newlines embedded in message")
	cmd.Flags().BoolVarP(&curOptions.TruncateComponent, "truncatecomponent", "t", false, "truncate component name")
	cmd.Flags().BoolVar(&curOptions.TruncateComponent, "tc", false, "")
	cmd.Flags().StringVarP(&curOptions.OnlyHostname, "host", "H", "", "select only one host")
	cmd.Flags().StringVarP(&curOptions.OnlyPid, "pid", "p", "", "select only one process ID")
	cmd.Flags().StringVarP(&curOptions.OnlyService, "service", "S", "", "select only one service")
	cmd.Flags().VarP(&curOptions.Format, "format", "F", "select logfmt output format, vespa (default), json or raw are supported. The json output format is not stable, and will change in the future.")
	cmd.Flags().MarkHidden("tc")
	cmd.Flags().MarkHidden("ts")
	cmd.Flags().MarkHidden("dequotenewlines")
	return cmd
}
