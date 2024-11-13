// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa query command
// author: bratseth

package cmd

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/curl"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
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
		postFile         string
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
		Args:              cobra.MinimumNArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) == 0 && postFile == "" {
				return fmt.Errorf("requires at least 1 arg")
			}
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			return query(cli, args, queryTimeoutSecs, printCurl, format, postFile, headers, waiter)
		},
	}
	cmd.Flags().BoolVarP(&printCurl, "verbose", "v", false, "Print the equivalent curl command for the query")
	cmd.Flags().StringVarP(&postFile, "file", "", "", "Read query parameters from the given JSON file and send a POST request, with overrides from arguments")
	cmd.Flags().StringVarP(&format, "format", "", "human", "Output format. Must be 'human' (human-readable) or 'plain' (no formatting)")
	cmd.Flags().StringSliceVarP(&headers, "header", "", nil, "Add a header to the HTTP request, on the format 'Header: Value'. This can be specified multiple times")
	cmd.Flags().IntVarP(&queryTimeoutSecs, "timeout", "T", 10, "Timeout for the query in seconds")
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func printCurl(stderr io.Writer, req *http.Request, postFile string, service *vespa.Service) error {
	cmd, err := curl.RawArgs(req.URL.String())
	if err != nil {
		return err
	}
	cmd.Method = req.Method
	if postFile != "" {
		cmd.WithBodyFile(postFile)
	}
	for k, vl := range req.Header {
		for _, v := range vl {
			cmd.Header(k, v)
		}
	}
	cmd.Certificate = service.TLSOptions.CertificateFile
	cmd.PrivateKey = service.TLSOptions.PrivateKeyFile
	_, err = io.WriteString(stderr, cmd.String()+"\n")
	return err
}

func query(cli *CLI, arguments []string, timeoutSecs int, curl bool, format string, postFile string, headers []string, waiter *Waiter) error {
	target, err := cli.target(targetOptions{})
	if err != nil {
		return err
	}
	service, err := waiter.Service(target, cli.config.cluster())
	if err != nil {
		return err
	}
	switch format {
	case "plain", "human":
	default:
		return fmt.Errorf("invalid format: %s", format)
	}
	url, _ := url.Parse(strings.TrimSuffix(service.BaseURL, "/") + "/search/")
	urlQuery := url.Query()
	for i := range len(arguments) {
		key, value := splitArg(arguments[i])
		urlQuery.Set(key, value)
	}
	queryTimeout := urlQuery.Get("timeout")
	if queryTimeout == "" {
		// No timeout set by user, use the timeout option
		queryTimeout = fmt.Sprintf("%ds", timeoutSecs)
		urlQuery.Set("timeout", queryTimeout)
	}
	deadline, err := time.ParseDuration(queryTimeout)
	if err != nil {
		return fmt.Errorf("invalid query timeout: %w", err)
	}
	header, err := httputil.ParseHeader(headers)
	if err != nil {
		return err
	}
	hReq := &http.Request{Header: header, URL: url}
	if postFile != "" {
		json, err := getJsonFrom(postFile, urlQuery)
		if err != nil {
			return fmt.Errorf("bad JSON in postFile '%s': %w", postFile, err)
		}
		header.Set("Content-Type", "application/json")
		hReq.Method = "POST"
		hReq.Body = io.NopCloser(bytes.NewBuffer(bytes.Clone(json)))
		if err != nil {
			return fmt.Errorf("bad postFile '%s': %w", postFile, err)
		}
	}
	url.RawQuery = urlQuery.Encode()
	if curl {
		if err := printCurl(cli.Stderr, hReq, postFile, service); err != nil {
			return err
		}
	}
	response, err := service.Do(hReq, deadline+time.Second) // Slightly longer than query timeout
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
		_, err := io.Copy(cli.Stdout, body)
		return err
	} else if options.tokenStream {
		bufSize := 1024 * 1024 // Handle events up to this size
		dec := sse.NewDecoderSize(body, bufSize)
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

func getJsonFrom(fn string, query url.Values) ([]byte, error) {
	parsed := make(map[string]any)
	f, err := os.Open(fn)
	if err != nil {
		return nil, err
	}
	body, err := io.ReadAll(f)
	if err != nil {
		return nil, err
	}
	err = json.Unmarshal(body, &parsed)
	if err != nil {
		return nil, err
	}
	for k, vl := range query {
		if len(vl) == 1 {
			parsed[k] = vl[0]
		} else {
			parsed[k] = vl
		}
		query.Del(k)
	}
	b, err := json.Marshal(parsed)
	if err != nil {
		return nil, err
	}
	return b, nil
}
