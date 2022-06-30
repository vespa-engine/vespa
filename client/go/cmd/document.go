// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
	"fmt"
	"io"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func addDocumentFlags(cmd *cobra.Command, printCurl *bool, timeoutSecs *int) {
	cmd.PersistentFlags().BoolVarP(printCurl, "verbose", "v", false, "Print the equivalent curl command for the document operation")
	cmd.PersistentFlags().IntVarP(timeoutSecs, "timeout", "T", 60, "Timeout for the document request in seconds")
}

func newDocumentCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:   "document json-file",
		Short: "Issue a document operation to Vespa",
		Long: `Issue a document operation to Vespa.

The operation must be on the format documented in
https://docs.vespa.ai/en/reference/document-json-format.html#document-operations

When this returns successfully, the document is guaranteed to be visible in any
subsequent get or query operation.

To feed with high throughput, https://docs.vespa.ai/en/vespa-feed-client.html
should be used instead of this.`,
		Example:           `$ vespa document src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			service, err := documentService(cli)
			if err != nil {
				return err
			}
			return printResult(cli, vespa.Send(args[0], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), false)
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentPutCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:   "put [id] json-file",
		Short: "Writes a document to Vespa",
		Long: `Writes the document in the given file to Vespa.
If the document already exists, all its values will be replaced by this document.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
		Args: cobra.RangeArgs(1, 2),
		Example: `$ vespa document put src/test/resources/A-Head-Full-of-Dreams.json
$ vespa document put id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			service, err := documentService(cli)
			if err != nil {
				return err
			}
			if len(args) == 1 {
				return printResult(cli, vespa.Put("", args[0], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), false)
			} else {
				return printResult(cli, vespa.Put(args[0], args[1], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), false)
			}
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentUpdateCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:   "update [id] json-file",
		Short: "Modifies some fields of an existing document",
		Long: `Updates the values of the fields given in a json file as specified in the file.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
		Args: cobra.RangeArgs(1, 2),
		Example: `$ vespa document update src/test/resources/A-Head-Full-of-Dreams-Update.json
$ vespa document update id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			service, err := documentService(cli)
			if err != nil {
				return err
			}
			if len(args) == 1 {
				return printResult(cli, vespa.Update("", args[0], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), false)
			} else {
				return printResult(cli, vespa.Update(args[0], args[1], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), false)
			}
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentRemoveCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:   "remove id | json-file",
		Short: "Removes a document from Vespa",
		Long: `Removes the document specified either as a document id or given in the json file.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
		Args: cobra.ExactArgs(1),
		Example: `$ vespa document remove src/test/resources/A-Head-Full-of-Dreams-Remove.json
$ vespa document remove id:mynamespace:music::a-head-full-of-dreams`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			service, err := documentService(cli)
			if err != nil {
				return err
			}
			if strings.HasPrefix(args[0], "id:") {
				return printResult(cli, vespa.RemoveId(args[0], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), false)
			} else {
				return printResult(cli, vespa.RemoveOperation(args[0], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), false)
			}
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentGetCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:               "get id",
		Short:             "Gets a document",
		Args:              cobra.ExactArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Example:           `$ vespa document get id:mynamespace:music::a-head-full-of-dreams`,
		RunE: func(cmd *cobra.Command, args []string) error {
			service, err := documentService(cli)
			if err != nil {
				return err
			}
			return printResult(cli, vespa.Get(args[0], service, operationOptions(cli.Stderr, printCurl, timeoutSecs)), true)
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func documentService(cli *CLI) (*vespa.Service, error) {
	target, err := cli.target(targetOptions{})
	if err != nil {
		return nil, err
	}
	return cli.service(target, vespa.DocumentService, 0, cli.config.cluster())
}

func operationOptions(stderr io.Writer, printCurl bool, timeoutSecs int) vespa.OperationOptions {
	curlOutput := io.Discard
	if printCurl {
		curlOutput = stderr
	}
	return vespa.OperationOptions{
		CurlOutput: curlOutput,
		Timeout:    time.Second * time.Duration(timeoutSecs),
	}
}

func printResult(cli *CLI, result util.OperationResult, payloadOnlyOnSuccess bool) error {
	out := cli.Stdout
	if !result.Success {
		out = cli.Stderr
	}

	if !result.Success {
		fmt.Fprintln(out, color.RedString("Error:"), result.Message)
	} else if !(payloadOnlyOnSuccess && result.Payload != "") {
		fmt.Fprintln(out, color.GreenString("Success:"), result.Message)
	}

	if result.Detail != "" {
		fmt.Fprintln(out, color.YellowString(result.Detail))
	}

	if result.Payload != "" {
		if !payloadOnlyOnSuccess {
			fmt.Fprintln(out)
		}
		fmt.Fprintln(out, result.Payload)
	}

	if !result.Success {
		err := errHint(fmt.Errorf("document operation failed"))
		err.quiet = true
		return err
	}
	return nil
}
