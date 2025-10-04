// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa count command

package cmd

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/curl"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
)

type countOptions struct {
	documentType string
	waitSecs     int
	verbose      bool
}

func newCountCmd(cli *CLI) *cobra.Command {
	opts := countOptions{}
	cmd := &cobra.Command{
		Use:   "count",
		Short: "Count documents in the configured Vespa application",
		Long: `Count documents in the configured Vespa application.

This command provides a quick way to get the total number of documents
in your currently configured Vespa application. You can optionally filter by document type.

Note: The count may be approximate if match-phase limiting is enabled in your Vespa configuration.
For exact counts, consider using 'vespa visit' command or disabling match-phase limiting.`,
		Example: `$ vespa count
$ vespa count --document-type section
$ vespa count --verbose`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			waiter := cli.waiter(time.Duration(opts.waitSecs)*time.Second, cmd)
			return countDocuments(cli, &opts, waiter)
		},
	}

	addCountFlags(cli, cmd, &opts)

	return cmd
}

func addCountFlags(cli *CLI, cmd *cobra.Command, opts *countOptions) {
	cmd.Flags().BoolVarP(&opts.verbose, "verbose", "v", false, "Print the equivalent curl command and additional info for the count operation")
	cmd.Flags().StringVarP(&opts.documentType, "document-type", "d", "", "Filter by document type (e.g., 'section')")
	cli.bindWaitFlag(cmd, 0, &opts.waitSecs)
}

func countDocuments(cli *CLI, opts *countOptions, waiter *Waiter) error {
	// Get the configured target
	target, err := cli.target(targetOptions{})
	if err != nil {
		return err
	}

	// Get authentication method and service
	authMethod := cli.selectAuthMethod()
	service, err := waiter.ServiceWithAuthMethod(target, cli.config.cluster(), authMethod)
	if err != nil {
		return err
	}

	// Build YQL query
	yqlQuery := "select * from sources * where true limit 0"
	if opts.documentType != "" {
		yqlQuery = fmt.Sprintf("select * from sources * where sddocname contains \"%s\" limit 0", opts.documentType)
	}

	// Build request URL
	url, _ := url.Parse(strings.TrimSuffix(service.BaseURL, "/") + "/search/")
	urlQuery := url.Query()
	urlQuery.Set("yql", yqlQuery)
	urlQuery.Set("timeout", "10s")

	header, err := httputil.ParseHeader([]string{})
	if err != nil {
		return err
	}

	if authMethod == "token" {
		err = cli.addBearerToken(&header)
		if err != nil {
			return err
		}
		service.TLSOptions.CertificateFile = ""
		service.TLSOptions.PrivateKeyFile = ""
	}

	hReq := &http.Request{
		Method: "GET",
		URL:    url,
		Header: header,
	}
	url.RawQuery = urlQuery.Encode()

	// Print curl command if verbose
	if opts.verbose {
		cmd, err := curl.RawArgs(url.String())
		if err != nil {
			return err
		}
		cmd.Method = hReq.Method
		for k, vl := range hReq.Header {
			for _, v := range vl {
				cmd.Header(k, v)
			}
		}
		if service.AuthMethod == "mtls" {
			cmd.Certificate = service.TLSOptions.CertificateFile
			cmd.PrivateKey = service.TLSOptions.PrivateKeyFile
		}
		fmt.Fprintf(cli.Stderr, "%s\n", cmd.String())
	}

	// Make the request
	response, err := service.Do(hReq, 11*time.Second) // Slightly longer than query timeout
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		return fmt.Errorf("query failed with status %s: %s", response.Status, ioutil.ReaderToJSON(response.Body))
	}

	// Parse response
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return fmt.Errorf("failed to read response: %w", err)
	}

	var result struct {
		Root struct {
			Fields struct {
				TotalCount int64 `json:"totalCount"`
			} `json:"fields"`
			Coverage struct {
				Coverage    int  `json:"coverage"`
				Documents   int  `json:"documents"`
				Full        bool `json:"full"`
				Nodes       int  `json:"nodes"`
				Results     int  `json:"results"`
				ResultsFull int  `json:"resultsFull"`
				Degraded    struct {
					MatchPhase bool `json:"match-phase"`
				} `json:"degraded"`
			} `json:"coverage"`
		} `json:"root"`
	}

	if err := json.Unmarshal(body, &result); err != nil {
		return fmt.Errorf("failed to parse response: %w", err)
	}

	count := result.Root.Fields.TotalCount
	coverage := result.Root.Coverage

	if opts.verbose {
		if opts.documentType != "" {
			fmt.Fprintf(cli.Stderr, "Document count for type '%s': %s\n",
				color.CyanString(opts.documentType),
				color.GreenString("%d", count))
		} else {
			fmt.Fprintf(cli.Stderr, "Total document count: %s\n",
				color.GreenString("%d", count))
		}

		fmt.Fprintf(cli.Stderr, "Coverage: %d%%\n", coverage.Coverage)

		if coverage.Degraded.MatchPhase {
			fmt.Fprintf(cli.Stderr, "Warning: Count may be inaccurate due to match-phase limiting\n")
		}
	} else {
		// Non-verbose: just output the count
		fmt.Fprintf(cli.Stdout, "%d\n", count)

		// Always warn in non-verbose mode if there are accuracy issues
		if coverage.Degraded.MatchPhase {
			fmt.Fprintf(cli.Stderr, "Warning: Count may be inaccurate due to match-phase limiting\n")
		}
		if coverage.Coverage < 100 {
			fmt.Fprintf(cli.Stderr, "Warning: Incomplete coverage (%d%%)\n", coverage.Coverage)
		}
	}
	return nil
}
