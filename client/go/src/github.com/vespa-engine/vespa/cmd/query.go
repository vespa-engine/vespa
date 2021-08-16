// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa query command
// author: bratseth

package cmd

import (
    "errors"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "strings"
)

func init() {
    rootCmd.AddCommand(queryCmd)
}

var queryCmd = &cobra.Command{
    Use:   "query",
    Short: "Issue a query to Vespa",
    Long:  `TODO`,
    Args: func(cmd *cobra.Command, args []string) error {
        if len(args) != 1 {
          return errors.New("vespa query requires a single argument containing the query string")
        }
        return nil
    },
    Run: func(cmd *cobra.Command, args []string) {
        query(args[0])
    },
}

func query(argument string) {
    if ! strings.Contains(argument, "query=") {
        argument = "?query=" + argument
    }
    if ! strings.HasPrefix(argument, "?") {
        argument = "?" + argument
    }

    path := "/search/" + argument
    response := utils.HttpGet(GetTarget(queryContext).query, path, "Container")
    if (response == nil) {
        return
    }

    if (response.StatusCode == 200) {
        utils.Print("TODO: Print response data")
    } else if response.StatusCode % 100 == 4 {
        utils.Error("Invalid query (status ", response.Status, ")")
        utils.Detail("TODO: Print response data")
    } else {
        utils.Error("Request failed")
        utils.Detail(response.Status)
    }
}
