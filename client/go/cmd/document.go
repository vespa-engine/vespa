// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
	"log"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/util"
	"github.com/vespa-engine/vespa/vespa"
)

func init() {
	rootCmd.AddCommand(documentCmd)
	documentCmd.AddCommand(documentPutCmd)
	documentCmd.AddCommand(documentGetCmd)
}

var documentCmd = &cobra.Command{
	Use:     "document",
	Short:   "Issues the document operation in the given file to Vespa",
	Example: `$ vespa document src/test/resources/A-Head-Full-of-Dreams.json`,
	Args:    cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		printResult(vespa.Put("", args[0], documentTarget()), false) // TODO: Use Send
	},
}

var documentPutCmd = &cobra.Command{
	Use:   "put",
	Short: "Writes the document in the given file to Vespa",
	Args:  cobra.RangeArgs(1, 2),
	Example: `$ vespa document put src/test/resources/A-Head-Full-of-Dreams.json
$ vespa document put id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 1 {
			printResult(vespa.Put("", args[0], documentTarget()), false)
		} else {
			printResult(vespa.Put(args[0], args[1], documentTarget()), false)
		}
	},
}

var documentGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Gets a document",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		printResult(vespa.Get(args[0], documentTarget()), true)
	},
}

func printResult(result util.OperationResult, payloadOnlyOnSuccess bool) {
	if !result.Success {
		log.Print(color.Red("Error: "), result.Message)
	} else if !(payloadOnlyOnSuccess && result.Payload != "") {
		log.Print(color.Green("Success: "), result.Message)
	}

	if result.Detail != "" {
		log.Print(color.Brown(result.Detail))
	}

	if result.Payload != "" {
		if !payloadOnlyOnSuccess {
			log.Println("")
		}
		log.Print(result.Payload)
	}
}
