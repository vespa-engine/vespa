package cmd

import (
	"log"
	"runtime"

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
		log.Printf("vespa version %s compiled with %v on %v/%v", build.Version, runtime.Version(), runtime.GOOS, runtime.GOARCH)
	},
}
