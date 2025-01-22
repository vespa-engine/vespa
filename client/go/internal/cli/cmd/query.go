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

type queryOptions struct {
	printCurl        bool
	queryTimeoutSecs int
	waitSecs         int
	format           string
	postFile         string
	headers          []string
	profile          bool
	profileFile      string
}

func newQueryCmd(cli *CLI) *cobra.Command {
	opts := queryOptions{}
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
			if len(args) == 0 && opts.postFile == "" {
				return fmt.Errorf("requires at least 1 arg")
			}
			waiter := cli.waiter(time.Duration(opts.waitSecs)*time.Second, cmd)
			return query(cli, args, &opts, waiter)
		},
	}
	cmd.Flags().BoolVarP(&opts.printCurl, "verbose", "v", false, "Print the equivalent curl command for the query")
	cmd.Flags().StringVarP(&opts.postFile, "file", "", "", "Read query parameters from the given JSON file and send a POST request, with overrides from arguments")
	cmd.Flags().StringVarP(&opts.format, "format", "", "human", "Output format. Must be 'human' (human-readable) or 'plain' (no formatting)")
	cmd.Flags().StringSliceVarP(&opts.headers, "header", "", nil, "Add a header to the HTTP request, on the format 'Header: Value'. This can be specified multiple times")
	cmd.Flags().IntVarP(&opts.queryTimeoutSecs, "timeout", "T", 10, "Timeout for the query in seconds")
	cmd.Flags().BoolVarP(&opts.profile, "profile", "", false, "Enable profiling mode (Note: this feature is experimental)")
	cmd.Flags().StringVarP(&opts.profileFile, "profile-file", "", "vespa_query_profile_result.json", "Profiling result file")
	cli.bindWaitFlag(cmd, 0, &opts.waitSecs)
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

func query(cli *CLI, arguments []string, opts *queryOptions, waiter *Waiter) error {
	target, err := cli.target(targetOptions{})
	if err != nil {
		return err
	}
	service, err := waiter.Service(target, cli.config.cluster())
	if err != nil {
		return err
	}
	switch opts.format {
	case "plain", "human":
	default:
		return fmt.Errorf("invalid format: %s", opts.format)
	}
	url, _ := url.Parse(strings.TrimSuffix(service.BaseURL, "/") + "/search/")
	urlQuery := url.Query()
	for i := range len(arguments) {
		key, value := splitArg(arguments[i])
		urlQuery.Set(key, value)
	}
	if opts.profile {
		opts.format = "plain"
		opts.queryTimeoutSecs *= 2
		urlQuery.Set("trace.level", "1")
		urlQuery.Set("trace.explainLevel", "1")
		urlQuery.Set("trace.profiling.matching.depth", "100")
	}
	queryTimeout := urlQuery.Get("timeout")
	if queryTimeout == "" {
		// No timeout set by user, use the timeout option
		queryTimeout = fmt.Sprintf("%ds", opts.queryTimeoutSecs)
		urlQuery.Set("timeout", queryTimeout)
	}
	deadline, err := time.ParseDuration(queryTimeout)
	if err != nil {
		return fmt.Errorf("invalid query timeout: %w", err)
	}
	header, err := httputil.ParseHeader(opts.headers)
	if err != nil {
		return err
	}
	hReq := &http.Request{Header: header, URL: url}
	if opts.postFile != "" {
		json, err := getJsonFrom(opts.postFile, urlQuery)
		if err != nil {
			return fmt.Errorf("bad JSON in postFile '%s': %w", opts.postFile, err)
		}
		header.Set("Content-Type", "application/json")
		hReq.Method = "POST"
		hReq.Body = io.NopCloser(bytes.NewBuffer(bytes.Clone(json)))
		if err != nil {
			return fmt.Errorf("bad postFile '%s': %w", opts.postFile, err)
		}
	}
	url.RawQuery = urlQuery.Encode()
	if opts.printCurl {
		if err := printCurl(cli.Stderr, hReq, opts.postFile, service); err != nil {
			return err
		}
	}
	response, err := service.Do(hReq, deadline+time.Second) // Slightly longer than query timeout
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer response.Body.Close()

	if response.StatusCode == 200 {
		var output io.Writer = cli.Stdout
		if opts.profile {
			profileFile, err := os.Create(opts.profileFile)
			if err != nil {
				return fmt.Errorf("failed to create profile file %s: %w", opts.profileFile, err)
			}
			defer profileFile.Close()
			fmt.Fprintf(cli.Stderr, "writing profiling results to: %s\n", opts.profileFile)
			output = profileFile
		}
		if err := printResponse(response.Body, response.Header.Get("Content-Type"), opts.format, output); err != nil {
			return err
		}
	} else if response.StatusCode/100 == 4 {
		return fmt.Errorf("invalid query: %s\n%s", response.Status, ioutil.ReaderToJSON(response.Body))
	} else {
		return fmt.Errorf("%s from container at %s\n%s", response.Status, color.CyanString(url.Host), ioutil.ReaderToJSON(response.Body))
	}
	return nil
}

func printResponse(body io.Reader, contentType, format string, output io.Writer) error {
	contentType = strings.Split(contentType, ";")[0]
	if contentType == "text/event-stream" {
		return printResponseBody(body, printOptions{
			plainStream: format == "plain",
			tokenStream: format == "human",
		}, output)
	}
	return printResponseBody(body, printOptions{parseJSON: format == "human"}, output)
}

type printOptions struct {
	plainStream bool
	tokenStream bool
	parseJSON   bool
}

func printResponseBody(body io.Reader, options printOptions, output io.Writer) error {
	if options.plainStream {
		_, err := io.Copy(output, body)
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
				fmt.Fprint(output, value)
			} else if !event.IsEnd() {
				if writingLine {
					fmt.Fprintln(output)
				}
				event.Data = ioutil.StringToJSON(event.Data) // Optimistically pretty-print JSON
				fmt.Fprint(output, event.String())
			} else {
				fmt.Fprintln(output)
				break
			}
		}
		return nil
	} else if options.parseJSON {
		text := ioutil.ReaderToJSON(body) // Optimistic, returns body as the raw string if it cannot be parsed to JSON
		fmt.Fprintln(output, text)
		return nil
	} else {
		b, err := io.ReadAll(body)
		if err != nil {
			return err
		}
		fmt.Fprintln(output, string(b))
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
