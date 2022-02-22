// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa query command
// author: bratseth

package cmd

import (
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/curl"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

var (
	queryPrintCurl   bool
	queryTimeoutSecs int
)

func init() {
	rootCmd.AddCommand(queryCmd)
	queryCmd.PersistentFlags().BoolVarP(&queryPrintCurl, "verbose", "v", false, "Print the equivalent curl command for the query")
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
	SilenceUsage:      true,
	Args:              cobra.MinimumNArgs(1),
	RunE:              query,
}

func printCurl(url string, service *vespa.Service) error {
	out := ioutil.Discard
	if queryPrintCurl {
		out = stderr
	}
	cmd, err := curl.RawArgs(url)
	if err != nil {
		return err
	}
	cmd.Certificate = service.TLSOptions.CertificateFile
	cmd.PrivateKey = service.TLSOptions.PrivateKeyFile
	_, err = io.WriteString(out, cmd.String()+"\n")
	return err
}

func query(cmd *cobra.Command, arguments []string) error {
	service, err := getService("query", 0, "")
	if err != nil {
		return err
	}
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
		return fmt.Errorf("invalid query timeout: %w", err)
	}
	if err := printCurl(url.String(), service); err != nil {
		return err
	}
	response, err := service.Do(&http.Request{URL: url}, deadline+time.Second) // Slightly longer than query timeout
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer response.Body.Close()

	if response.StatusCode == 200 {
		log.Print(util.ReaderToJSON(response.Body))
	} else if response.StatusCode/100 == 4 {
		return fmt.Errorf("invalid query: %s\n%s", response.Status, util.ReaderToJSON(response.Body))
	} else {
		return fmt.Errorf("%s from container at %s\n%s", response.Status, color.Cyan(url.Host), util.ReaderToJSON(response.Body))
	}
	return nil
}

func splitArg(argument string) (string, string) {
	equalsIndex := strings.Index(argument, "=")
	if equalsIndex < 1 {
		return "yql", argument
	} else {
		return argument[0:equalsIndex], argument[equalsIndex+1:]
	}
}
