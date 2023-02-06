package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
)

func newAuthCmd() *cobra.Command {
	return &cobra.Command{
		Use:               "auth",
		Short:             "Manage Vespa Cloud credentials",
		Long:              `Manage Vespa Cloud credentials.`,
		DisableAutoGenTag: true,
		SilenceUsage:      false,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}
}
