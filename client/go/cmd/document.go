// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
	"log"
	"strings"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func init() {
	rootCmd.AddCommand(documentCmd)
	documentCmd.AddCommand(documentPutCmd)
	documentCmd.AddCommand(documentUpdateCmd)
	documentCmd.AddCommand(documentRemoveCmd)
	documentCmd.AddCommand(documentGetCmd)
}

var documentCmd = &cobra.Command{
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
	Args:              cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		printResult(vespa.Send(args[0], documentService()), false)
	},
}

var documentPutCmd = &cobra.Command{
	Use:   "put [id] json-file",
	Short: "Writes a document to Vespa",
	Long: `Writes the document in the given file to Vespa.
If the document already exists, all its values will be replaced by this document.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
	Args: cobra.RangeArgs(1, 2),
	Example: `$ vespa document put src/test/resources/A-Head-Full-of-Dreams.json
$ vespa document put id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 1 {
			printResult(vespa.Put("", args[0], documentService()), false)
		} else {
			printResult(vespa.Put(args[0], args[1], documentService()), false)
		}
	},
}

var documentUpdateCmd = &cobra.Command{
	Use:   "update [id] json-file",
	Short: "Modifies some fields of an existing document",
	Long: `Updates the values of the fields given in a json file as specified in the file.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
	Args: cobra.RangeArgs(1, 2),
	Example: `$ vespa document update src/test/resources/A-Head-Full-of-Dreams-Update.json
$ vespa document update id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 1 {
			printResult(vespa.Update("", args[0], documentService()), false)
		} else {
			printResult(vespa.Update(args[0], args[1], documentService()), false)
		}
	},
}

var documentRemoveCmd = &cobra.Command{
	Use:   "remove id | json-file",
	Short: "Removes a document from Vespa",
	Long: `Removes the document specified either as a document id or given in the json file.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
	Args: cobra.ExactArgs(1),
	Example: `$ vespa document remove src/test/resources/A-Head-Full-of-Dreams-Remove.json
$ vespa document remove id:mynamespace:music::a-head-full-of-dreams`,
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		if strings.HasPrefix(args[0], "id:") {
			printResult(vespa.RemoveId(args[0], documentService()), false)
		} else {
			printResult(vespa.RemoveOperation(args[0], documentService()), false)
		}
	},
}

var documentGetCmd = &cobra.Command{
	Use:               "get id",
	Short:             "Gets a document",
	Args:              cobra.ExactArgs(1),
	DisableAutoGenTag: true,
	Example:           `$ vespa document get id:mynamespace:music::a-head-full-of-dreams`,
	Run: func(cmd *cobra.Command, args []string) {
		printResult(vespa.Get(args[0], documentService()), true)
	},
}

func documentService() *vespa.Service { return getService("document", 0) }

func printResult(result util.OperationResult, payloadOnlyOnSuccess bool) {
	if !result.Success {
		log.Print(color.Red("Error: "), result.Message)
	} else if !(payloadOnlyOnSuccess && result.Payload != "") {
		log.Print(color.Green("Success: "), result.Message)
	}

	if result.Detail != "" {
		log.Print(color.Yellow(result.Detail))
	}

	if result.Payload != "" {
		if !payloadOnlyOnSuccess {
			log.Println("")
		}
		log.Print(result.Payload)
	}
}
