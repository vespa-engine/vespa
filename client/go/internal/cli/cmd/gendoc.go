// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/spf13/cobra/doc"
)

func newGendocCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "gendoc directory",
		Short:             "Generate documentation from '--help' pages and write as markdown files to a given directory",
		Args:              cobra.ExactArgs(1),
		Hidden:            true, // Not intended to be called by users
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			dir := args[0]
			err := doc.GenMarkdownTree(cli.cmd, dir)
			if err != nil {
				return fmt.Errorf("failed to write documentation pages: %w", err)
			}
			cli.printSuccess("Documentation pages written to ", dir)
			return nil
		},
	}
}
