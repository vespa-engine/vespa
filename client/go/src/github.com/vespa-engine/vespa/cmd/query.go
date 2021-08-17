// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa query command
// author: bratseth

package cmd

import (
    "bufio"
    "errors"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "strings"
)

func init() {
    rootCmd.AddCommand(queryCmd)
}

var queryCmd = &cobra.Command{
    Use:   "query \"select from sources * where title contains 'foo'&hits=5\"",
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
    response := utils.HttpGet(getTarget(queryContext).query, path, "Container")
    if (response == nil) {
        return
    }

    defer response.Body.Close()
    if (response.StatusCode == 200) {
        scanner := bufio.NewScanner(response.Body)
        for ;scanner.Scan(); {
            utils.Print(scanner.Text())
        }
        if err := scanner.Err(); err != nil {
            utils.Error(err.Error())
        }
    } else if response.StatusCode % 100 == 4 {
        utils.Error("Invalid query (status ", response.Status, ")")
        utils.Detail()
    } else {
        utils.Error("Request failed")
        utils.Detail(response.Status)
    }
}
