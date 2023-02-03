// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/spf13/cobra/doc"
)

func newManCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "man directory",
		Short:             "Generate man pages and write them to given directory",
		Args:              cobra.ExactArgs(1),
		Hidden:            true, // Not intended to be called by users
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			dir := args[0]
			err := doc.GenManTree(cli.cmd, nil, dir)
			if err != nil {
				return fmt.Errorf("failed to write man pages: %w", err)
			}
			cli.printSuccess("Man pages written to ", dir)
			return nil
		},
	}
}
