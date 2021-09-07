package cmd

import (
	"log"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/build"
)

func init() {
	rootCmd.AddCommand(versionCmd)
}

var versionCmd = &cobra.Command{
	Use:               "version",
	Short:             "Show version number",
	DisableAutoGenTag: true,
	Args:              cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		log.Print("vespa version ", build.Version)
	},
}
