package cmd

import (
	"bufio"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/tracedoctor"
	"os"
)

type inspectProfileOptions struct {
	profileFile string
}

func inspectProfile(cli *CLI, opts *inspectProfileOptions) error {
	file, err := os.Open(opts.profileFile)
	if err != nil {
		return fmt.Errorf("failed to open profile file '%s': %w", opts.profileFile, err)
	}
	defer file.Close()
	root := slime.DecodeJson(bufio.NewReaderSize(file, 64*1024))
	if !root.Valid() {
		return fmt.Errorf("profile file '%s' does not contain valid JSON", opts.profileFile)
	}
	context := tracedoctor.NewContext(root)
	return context.Analyze(cli.Stdout)
}

func newInspectProfileCmd(cli *CLI) *cobra.Command {
	opts := inspectProfileOptions{}

	cmd := &cobra.Command{
		Use:    "profile",
		Hidden: true,
		Short:  "Inspect profiling results",
		Long:   `Inspect profiling results previously obtained by vespa query --profile`,
		RunE: func(cmd *cobra.Command, args []string) error {
			return inspectProfile(cli, &opts)
		},
	}

	cmd.Flags().StringVarP(&opts.profileFile, "profile-file", "f", "vespa_query_profile_result.json", "Name of the profile file to inspect")
	return cmd
}

func newInspectCmd(cli *CLI) *cobra.Command {
	cmd := &cobra.Command{
		Use:    "inspect",
		Hidden: true,
		Short:  "Provides insight",
		Long:   "Provides subcommands to inspect various things in more detail",
	}
	cmd.AddCommand(newInspectProfileCmd(cli))
	return cmd
}
