// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa query command
// author: bratseth

package cmd

import (
    "bufio"
    "errors"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "regexp"
    "strings"
    "net/url"
)

func init() {
    rootCmd.AddCommand(queryCmd)
}

var queryCmd = &cobra.Command{
    Use:   "query",
    Short: "Issue a query to Vespa",
    Long:  `TODO, example  \"yql=select from sources * where title contains 'foo'\" hits=5`,
    // TODO: Support referencing a query json file
    Args: func(cmd *cobra.Command, args []string) error {
        if len(args) < 1 {
            return errors.New("vespa query requires at least one argument containing the query string")
        }
        return nil
    },
    Run: func(cmd *cobra.Command, args []string) {
        query(args)
    },
}

func query(arguments []string) {
    var argBuilder strings.Builder;
    for i := 0; i < len(arguments); i++ {
        argument := arguments[i]

        if ! startsByParameter(argument) { // Default parameter
            argument = "yql=" + argument
        }

        argument = escapePayload(argument)
        if argument == "" {
            return
        }
        if i > 0 {
            argBuilder.WriteString("&")
        }
        argBuilder.WriteString(argument)
    }

    path := "/search/?" + argBuilder.String()
    response := utils.HttpGet(getTarget(queryContext).query, path, "Container")
    if (response == nil) {
        return
    }
    defer response.Body.Close()

    if (response.StatusCode == 200) {
        // TODO: Pretty-print body
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

func startsByParameter(argument string) bool {
    match, _ := regexp.MatchString("[a-zA-Z0-9_]+=", argument) // TODO: Allow dot in parameters
    return match
}

func escapePayload(argument string) string {
    equalsIndex := strings.Index(argument, "=")
    if equalsIndex < 1 {
        utils.Error("A query argument must be on the form parameter=value, but was '" + argument + "'")
        return ""
    }
    return argument[0:equalsIndex] + "=" + url.QueryEscape(argument[equalsIndex + 1:len(argument)])
}
