// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
	"log"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/vespa"
)

func init() {
	rootCmd.AddCommand(documentCmd)
	documentCmd.AddCommand(documentPostCmd)
	documentCmd.AddCommand(documentGetCmd)
	addTargetFlag(documentCmd)
}

var documentCmd = &cobra.Command{
	Use:   "document",
	Short: "Issue document operations",
	Long:  `TODO: Example vespa document mynamespace/mydocumenttype/myid document.json`,
	// TODO: Check args
	Run: func(cmd *cobra.Command, args []string) {
		post("", args[0])
	},
}

var documentPostCmd = &cobra.Command{
	Use:   "post",
	Short: "Posts the document in the given file",
	Long:  `TODO`,
	// TODO: Check args
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 1 {
			post("", args[0])
		} else {
			post(args[0], args[1])
		}
	},
}

var documentGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Gets a document",
	Long:  `TODO`,
	// TODO: Check args
	Run: func(cmd *cobra.Command, args []string) {
		get(args[0])
	},
}

func get(documentId string) {
}

func post(documentId string, jsonFile string) {
	result := vespa.Post(documentId, jsonFile, documentTarget())

	if result.Success {
		log.Print(color.Green("Success: "), result.Message)
	} else {
		log.Print(color.Red("Error: "), result.Message)
	}

	if result.Detail != "" {
		log.Print(color.Brown(result.Detail))
	}

	if result.Payload != "" {
		log.Println("")
		log.Print(result.Payload)
	}

}
