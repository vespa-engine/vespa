// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

var (
	fromArg    string
	toArg      string
	levelArg   string
	followArg  bool
	dequoteArg bool
)

func init() {
	rootCmd.AddCommand(logCmd)
	logCmd.Flags().StringVarP(&fromArg, "from", "F", "", "Include logs since this timestamp (RFC3339 format)")
	logCmd.Flags().StringVarP(&toArg, "to", "T", "", "Include logs until this timestamp (RFC3339 format)")
	logCmd.Flags().StringVarP(&levelArg, "level", "l", "debug", `The maximum log level to show. Must be "error", "warning", "info" or "debug"`)
	logCmd.Flags().BoolVarP(&followArg, "follow", "f", false, "Follow logs")
	logCmd.Flags().BoolVarP(&dequoteArg, "nldequote", "n", true, "Dequote LF and TAB characters in log messages")
}

var logCmd = &cobra.Command{
	Use:   "log [relative-period]",
	Short: "Show the Vespa log",
	Long: `Show the Vespa log.

The logs shown can be limited to a relative or fixed period. All timestamps are shown in UTC.

Logs for the past hour are shown if no arguments are given.
`,
	Example: `$ vespa log 1h
$ vespa log --nldequote=false 10m
$ vespa log --from 2021-08-25T15:00:00Z --to 2021-08-26T02:00:00Z
$ vespa log --follow`,
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		target, err := getTarget()
		if err != nil {
			return err
		}
		options := vespa.LogOptions{
			Level:   vespa.LogLevel(levelArg),
			Follow:  followArg,
			Writer:  stdout,
			Dequote: dequoteArg,
		}
		if options.Follow {
			if fromArg != "" || toArg != "" || len(args) > 0 {
				return fmt.Errorf("cannot combine --from/--to or relative time with --follow")
			}
			options.From = time.Now().Add(-5 * time.Minute)
		} else {
			from, to, err := parsePeriod(args)
			if err != nil {
				return fmt.Errorf("invalid period: %w", err)
			}
			options.From = from
			options.To = to
		}
		if err := target.PrintLog(options); err != nil {
			return fmt.Errorf("could not retrieve logs: %w", err)
		}
		return nil
	},
}

func parsePeriod(args []string) (time.Time, time.Time, error) {
	relativePeriod := fromArg == "" || toArg == ""
	if relativePeriod {
		period := "1h"
		if len(args) > 0 {
			period = args[0]
		}
		d, err := time.ParseDuration(period)
		if err != nil {
			return time.Time{}, time.Time{}, err
		}
		if d > 0 {
			d = -d
		}
		to := time.Now()
		from := to.Add(d)
		return from, to, nil
	} else if len(args) > 0 {
		return time.Time{}, time.Time{}, fmt.Errorf("cannot combine --from/--to with relative value: %s", args[0])
	}
	from, err := time.Parse(time.RFC3339, fromArg)
	if err != nil {
		return time.Time{}, time.Time{}, err
	}
	to, err := time.Parse(time.RFC3339, toArg)
	if err != nil {
		return time.Time{}, time.Time{}, err
	}
	if !to.After(from) {
		return time.Time{}, time.Time{}, fmt.Errorf("--to must specify a time after --from")
	}
	return from, to, nil
}
