// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
)

func newSkillsListCmd(cli *CLI) *cobra.Command {
	var force bool
	cmd := &cobra.Command{
		Use:               "list",
		Short:             "List available Vespa AI-assistant skills",
		Args:              cobra.NoArgs,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			source := &skillsSource{cli: cli, noCache: force}
			zipPath, err := source.zipPath()
			if err != nil {
				return err
			}
			archive, err := openSkillsZip(zipPath)
			if err != nil {
				return err
			}
			defer archive.Close()
			skills, err := listAvailableSkills(&archive.Reader)
			if err != nil {
				return err
			}
			for _, s := range skills {
				fmt.Fprintf(cli.Stdout, "%s\n  %s\n", color.CyanString(s.name), s.description)
			}
			return nil
		},
	}
	cmd.Flags().BoolVarP(&force, "force", "f", false, "Ignore cache and force downloading the latest skills from GitHub")
	return cmd
}
