// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa query command
// author: bratseth

package cmd

import (
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
)

var queryTimeoutSecs int

func init() {
	rootCmd.AddCommand(queryCmd)
	queryCmd.Flags().IntVarP(&queryTimeoutSecs, "timeout", "T", 10, "Timeout for the query in seconds")
}

var queryCmd = &cobra.Command{
	Use:     "query query-parameters",
	Short:   "Issue a query to Vespa",
	Example: `$ vespa query "yql=select * from music where album contains 'head';" hits=5`,
	Long: `Issue a query to Vespa.

Any parameter from https://docs.vespa.ai/en/reference/query-api-reference.html
can be set by the syntax [parameter-name]=[value].`,
	// TODO: Support referencing a query json file
	DisableAutoGenTag: true,
	Args:              cobra.MinimumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		query(args)
	},
}

func query(arguments []string) {
	service := getService("query", 0, "")
	url, _ := url.Parse(service.BaseURL + "/search/")
	urlQuery := url.Query()
	for i := 0; i < len(arguments); i++ {
		key, value := splitArg(arguments[i])
		urlQuery.Set(key, value)
	}
	queryTimeout := urlQuery.Get("timeout")
	if queryTimeout == "" {
		// No timeout set by user, use the timeout option
		queryTimeout = fmt.Sprintf("%ds", queryTimeoutSecs)
		urlQuery.Set("timeout", queryTimeout)
	}
	url.RawQuery = urlQuery.Encode()
	deadline, err := time.ParseDuration(queryTimeout)
	if err != nil {
		fatalErr(err, "Invalid query timeout")
		return
	}
	response, err := service.Do(&http.Request{URL: url}, deadline+time.Second) // Slightly longer than query timeout
	if err != nil {
		fatalErr(nil, "Request failed: ", err)
		return
	}
	defer response.Body.Close()

	if response.StatusCode == 200 {
		log.Print(util.ReaderToJSON(response.Body))
	} else if response.StatusCode/100 == 4 {
		fatalErr(nil, "Invalid query: ", response.Status, "\n", util.ReaderToJSON(response.Body))
	} else {
		fatalErr(nil, response.Status, " from container at ", color.Cyan(url.Host), "\n", util.ReaderToJSON(response.Body))
	}
}

func splitArg(argument string) (string, string) {
	equalsIndex := strings.Index(argument, "=")
	if equalsIndex < 1 {
		return "yql", argument
	} else {
		return argument[0:equalsIndex], argument[equalsIndex+1:]
	}
}
