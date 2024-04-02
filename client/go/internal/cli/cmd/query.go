// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa query command
// author: bratseth

package cmd

import (
	"bufio"
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
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/sse"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newQueryCmd(cli *CLI) *cobra.Command {
	var (
		printCurl        bool
		queryTimeoutSecs int
		waitSecs         int
		format           string
		headers          []string
	)
	cmd := &cobra.Command{
		Use:   "query query-parameters",
		Short: "Issue a query to Vespa",
		Example: `$ vespa query "yql=select * from music where album contains 'head'" hits=5
$ vespa query --format=plain "yql=select * from music where album contains 'head'" hits=5
$ vespa query --header="X-First-Name: Joe" "yql=select * from music where album contains 'head'" hits=5`,
		Long: `Issue a query to Vespa.

Any parameter from https://docs.vespa.ai/en/reference/query-api-reference.html
can be set by the syntax [parameter-name]=[value].`,
		// TODO: Support referencing a query json file
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return query(cli, args, queryTimeoutSecs, waitSecs, printCurl, format, headers)
		},
	}
	cmd.Flags().BoolVarP(&printCurl, "verbose", "v", false, "Print the equivalent curl command for the query")
	cmd.Flags().StringVarP(&format, "format", "", "human", "Output format. Must be 'human' (human-readable) or 'plain' (no formatting)")
	cmd.Flags().StringSliceVarP(&headers, "header", "", nil, "Add a header to the HTTP request, on the format 'Header: Value'. This can be specified multiple times")
	cmd.Flags().IntVarP(&queryTimeoutSecs, "timeout", "T", 10, "Timeout for the query in seconds")
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func printCurl(stderr io.Writer, url string, service *vespa.Service) error {
	cmd, err := curl.RawArgs(url)
	if err != nil {
		return err
	}
	cmd.Certificate = service.TLSOptions.CertificateFile
	cmd.PrivateKey = service.TLSOptions.PrivateKeyFile
	_, err = io.WriteString(stderr, cmd.String()+"\n")
	return err
}

func parseHeaders(headers []string) (http.Header, error) {
	h := make(http.Header)
	for _, header := range headers {
		kv := strings.SplitN(header, ":", 2)
		if len(kv) < 2 {
			return nil, fmt.Errorf("invalid header %q: missing colon separator", header)
		}
		k := kv[0]
		v := strings.TrimSpace(kv[1])
		h.Add(k, v)
	}
	return h, nil
}

func query(cli *CLI, arguments []string, timeoutSecs, waitSecs int, curl bool, format string, headers []string) error {
	target, err := cli.target(targetOptions{})
	if err != nil {
		return err
	}
	waiter := cli.waiter(time.Duration(waitSecs) * time.Second)
	service, err := waiter.Service(target, cli.config.cluster())
	if err != nil {
		return err
	}
	switch format {
	case "plain", "human":
	default:
		return fmt.Errorf("invalid format: %s", format)
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
		queryTimeout = fmt.Sprintf("%ds", timeoutSecs)
		urlQuery.Set("timeout", queryTimeout)
	}
	url.RawQuery = urlQuery.Encode()
	deadline, err := time.ParseDuration(queryTimeout)
	if err != nil {
		return fmt.Errorf("invalid query timeout: %w", err)
	}
	if curl {
		if err := printCurl(cli.Stderr, url.String(), service); err != nil {
			return err
		}
	}
	header, err := parseHeaders(headers)
	if err != nil {
		return err
	}
	response, err := service.Do(&http.Request{Header: header, URL: url}, deadline+time.Second) // Slightly longer than query timeout
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer response.Body.Close()

	if response.StatusCode == 200 {
		if err := printResponse(response.Body, response.Header.Get("Content-Type"), format, cli); err != nil {
			return err
		}
	} else if response.StatusCode/100 == 4 {
		return fmt.Errorf("invalid query: %s\n%s", response.Status, ioutil.ReaderToJSON(response.Body))
	} else {
		return fmt.Errorf("%s from container at %s\n%s", response.Status, color.CyanString(url.Host), ioutil.ReaderToJSON(response.Body))
	}
	return nil
}

func printResponse(body io.Reader, contentType, format string, cli *CLI) error {
	contentType = strings.Split(contentType, ";")[0]
	if contentType == "text/event-stream" {
		return printResponseBody(body, printOptions{
			plainStream: format == "plain",
			tokenStream: format == "human",
		}, cli)
	}
	return printResponseBody(body, printOptions{parseJSON: format == "human"}, cli)
}

type printOptions struct {
	plainStream bool
	tokenStream bool
	parseJSON   bool
}

func printResponseBody(body io.Reader, options printOptions, cli *CLI) error {
	if options.plainStream {
		scanner := bufio.NewScanner(body)
		for scanner.Scan() {
			fmt.Fprintln(cli.Stdout, scanner.Text())
		}
		return scanner.Err()
	} else if options.tokenStream {
		dec := sse.NewDecoder(body)
		writingLine := false
		for {
			event, err := dec.Decode()
			if err == io.EOF {
				break
			} else if err != nil {
				return err
			}
			if event.Name == "token" {
				if !writingLine {
					writingLine = true
				}
				var token struct {
					Value string `json:"token"`
				}
				value := event.Data // Optimistic parsing
				if err := json.Unmarshal([]byte(event.Data), &token); err == nil {
					value = token.Value
				}
				fmt.Fprint(cli.Stdout, value)
			} else if !event.IsEnd() {
				if writingLine {
					fmt.Fprintln(cli.Stdout)
				}
				event.Data = ioutil.StringToJSON(event.Data) // Optimistically pretty-print JSON
				fmt.Fprint(cli.Stdout, event.String())
			} else {
				fmt.Fprintln(cli.Stdout)
				break
			}
		}
		return nil
	} else if options.parseJSON {
		text := ioutil.ReaderToJSON(body) // Optimistic, returns body as the raw string if it cannot be parsed to JSON
		fmt.Fprintln(cli.Stdout, text)
		return nil
	} else {
		b, err := io.ReadAll(body)
		if err != nil {
			return err
		}
		fmt.Fprintln(cli.Stdout, string(b))
		return nil
	}
}

func splitArg(argument string) (string, string) {
	parts := strings.SplitN(argument, "=", 2)
	if len(parts) < 2 {
		return "yql", parts[0]
	}
	if strings.HasPrefix(strings.ToLower(parts[0]), "select ") {
		// A query containing '='
		return "yql", argument
	}
	return parts[0], parts[1]
}
