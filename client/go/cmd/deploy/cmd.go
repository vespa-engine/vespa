// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/build"
	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func reallySimpleHelp(cmd *cobra.Command, args []string) {
	fmt.Println("Usage: vespa-deploy", cmd.Use)
}

func NewDeployCmd() *cobra.Command {
	var (
		curOptions Options
	)
	if err := vespa.LoadDefaultEnv(); err != nil {
		util.JustExitWith(err)
	}
	cobra.EnableCommandSorting = false
	cmd := &cobra.Command{
		Use:   "vespa-deploy [-h] [-v] [-f] [-t] [-c] [-p] [-z] [-V] [<command>] [args]",
		Short: "deploy applications to vespa config server",
		Long: `Usage: vespa-deploy [-h] [-v] [-f] [-t] [-c] [-p] [-z] [-V] [<command>] [args]
Supported commands: 'upload', 'prepare', 'activate', 'fetch' and 'help'
Supported options: '-h' (help), '-v' (verbose), '-f' (force/ignore validation errors), '-t' (timeout in seconds), '-p' (config server http port)
Try 'vespa-deploy help <command>' to get more help`,
		Version:           build.Version,
		Args:              cobra.MaximumNArgs(2),
		CompletionOptions: cobra.CompletionOptions{DisableDefaultCmd: true},
	}
	cmd.PersistentFlags().BoolVarP(&curOptions.Verbose, "verbose", "v", false, "show details")
	cmd.PersistentFlags().BoolVarP(&curOptions.DryRun, "dryrun", "n", false, "dry-run")
	cmd.PersistentFlags().BoolVarP(&curOptions.Force, "force", "f", false, "ignore validation errors")
	cmd.PersistentFlags().BoolVarP(&curOptions.Hosted, "hosted", "H", false, "for hosted vespa")

	cmd.PersistentFlags().StringVarP(&curOptions.ServerHost, "server", "c", "", "config server hostname")
	cmd.PersistentFlags().IntVarP(&curOptions.PortNumber, "port", "p", 19071, "config server http port")
	cmd.PersistentFlags().IntVarP(&curOptions.Timeout, "timeout", "t", 900, "timeout in seconds")

	cmd.PersistentFlags().StringVarP(&curOptions.Tenant, "tenant", "e", "default", "which tentant")
	cmd.PersistentFlags().StringVarP(&curOptions.Region, "region", "r", "default", "which region")
	cmd.PersistentFlags().StringVarP(&curOptions.Environment, "environment", "E", "prod", "which environment")
	cmd.PersistentFlags().StringVarP(&curOptions.Application, "application", "a", "default", "which application")
	cmd.PersistentFlags().StringVarP(&curOptions.Instance, "instance", "i", "default", "which instance")

	cmd.PersistentFlags().StringVarP(&curOptions.Rotations, "rotations", "R", "", "which rotations")
	cmd.PersistentFlags().StringVarP(&curOptions.VespaVersion, "vespaversion", "V", "", "which vespa version")

	cmd.PersistentFlags().MarkHidden("hosted")
	cmd.PersistentFlags().MarkHidden("rotations")
	cmd.PersistentFlags().MarkHidden("vespaversion")

	cmd.AddCommand(newUploadCmd(&curOptions))
	cmd.AddCommand(newPrepareCmd(&curOptions))
	cmd.AddCommand(newActivateCmd(&curOptions))
	cmd.AddCommand(newFetchCmd(&curOptions))

	cmd.InitDefaultHelpCmd()
	return cmd
}

func newUploadCmd(opts *Options) *cobra.Command {
	cmd := &cobra.Command{
		Use: "upload <application package>",
		Run: func(cmd *cobra.Command, args []string) {
			opts.Command = CmdUpload
			if opts.Verbose {
				trace.AdjustVerbosity(1)
			}
			trace.Trace("upload with", opts, args)
			err := RunUpload(opts, args)
			if err != nil {
				fmt.Fprintf(os.Stderr, "%s\n", err.Error())
				os.Exit(1)
			}
		},
		Args: cobra.MaximumNArgs(1),
	}
	cmd.Flags().StringVarP(&opts.From, "from", "F", "", `where from`)
	cmd.SetHelpFunc(reallySimpleHelp)
	return cmd
}

func newPrepareCmd(opts *Options) *cobra.Command {
	cmd := &cobra.Command{
		Use: "prepare [<session_id> | <application package>]",
		Run: func(cmd *cobra.Command, args []string) {
			opts.Command = CmdPrepare
			if opts.Verbose {
				trace.AdjustVerbosity(1)
			}
			trace.Trace("prepare with", opts, args)
			err := RunPrepare(opts, args)
			if err != nil {
				fmt.Fprintf(os.Stderr, "%s\n", err.Error())
				os.Exit(1)
			}
		},
		Args: cobra.MaximumNArgs(1),
	}
	cmd.SetHelpFunc(reallySimpleHelp)
	return cmd
}

func newActivateCmd(opts *Options) *cobra.Command {
	cmd := &cobra.Command{
		Use: "activate [<session_id>]",
		Run: func(cmd *cobra.Command, args []string) {
			opts.Command = CmdActivate
			if opts.Verbose {
				trace.AdjustVerbosity(1)
			}
			trace.Trace("activate with", opts, args)
			err := RunActivate(opts, args)
			if err != nil {
				fmt.Fprintf(os.Stderr, "%s\n", err.Error())
				os.Exit(1)
			}
		},
		Args: cobra.MaximumNArgs(1),
	}
	cmd.SetHelpFunc(reallySimpleHelp)
	return cmd
}

func newFetchCmd(opts *Options) *cobra.Command {
	cmd := &cobra.Command{
		Use: "fetch <output directory>",
		Run: func(cmd *cobra.Command, args []string) {
			opts.Command = CmdFetch
			if opts.Verbose {
				trace.AdjustVerbosity(1)
			}
			trace.Trace("fetch with", opts, args)
			err := RunFetch(opts, args)
			if err != nil {
				fmt.Fprintf(os.Stderr, "%s\n", err.Error())
				os.Exit(1)
			}
		},
		Args: cobra.MaximumNArgs(1),
	}
	cmd.SetHelpFunc(reallySimpleHelp)
	return cmd
}
