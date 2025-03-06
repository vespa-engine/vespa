// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/document"
)

func addDocumentFlags(cli *CLI, cmd *cobra.Command, printCurl *bool, timeoutSecs, waitSecs *int, headers *[]string) {
	cmd.PersistentFlags().BoolVarP(printCurl, "verbose", "v", false, "Print the equivalent curl command for the document operation")
	cmd.PersistentFlags().IntVarP(timeoutSecs, "timeout", "T", 60, "Timeout for the document request in seconds")
	cmd.PersistentFlags().StringSliceVarP(headers, "header", "", nil, "Add a header to the HTTP request, on the format 'Header: Value'. This can be specified multiple times")
	cli.bindWaitFlag(cmd, 0, waitSecs)
}

func documentClient(cli *CLI, timeoutSecs int, waiter *Waiter, printCurl bool, headers []string) (*document.Client, *vespa.Service, error) {
	docService, err := documentService(cli, waiter)
	if err != nil {
		return nil, nil, err
	}
	if printCurl {
		docService.CurlWriter = vespa.CurlWriter{Writer: cli.Stderr}
	}
	header, err := httputil.ParseHeader(headers)
	if err != nil {
		return nil, nil, err
	}
	authMethod := cli.selectAuthMethod()
	if authMethod == "token" {
		err = cli.addBearerToken(&header)
		if err != nil {
			return nil, nil, err
		}
		docService.TLSOptions.CertificateFile = ""
		docService.TLSOptions.PrivateKeyFile = ""
	}
	client, err := document.NewClient(document.ClientOptions{
		Compression: document.CompressionAuto,
		Timeout:     time.Duration(timeoutSecs) * time.Second,
		BaseURL:     docService.BaseURL,
		NowFunc:     time.Now,
		Header:      header,
	}, []httputil.Client{docService})
	if err != nil {
		return nil, nil, err
	}
	return client, docService, nil
}

func sendOperation(op document.Operation, args []string, timeoutSecs int, waiter *Waiter, printCurl bool, cli *CLI, headers []string) error {
	client, service, err := documentClient(cli, timeoutSecs, waiter, printCurl, headers)
	if err != nil {
		return err
	}
	id := ""
	filename := args[0]
	if len(args) > 1 {
		id = args[0]
		filename = args[1]
	}
	var r io.ReadCloser
	if filename == "-" {
		r = io.NopCloser(cli.Stdin)
	} else {
		f, err := os.Open(filename)
		if err != nil {
			return err
		}
		defer f.Close()
		r = f
	}
	doc, err := document.NewDecoder(r).Decode()
	if errors.Is(err, document.ErrMissingId) {
		if id == "" {
			return fmt.Errorf("no document id given neither as argument or as a 'put', 'update' or 'remove' key in the JSON file")
		}
	} else if err != nil {
		return err
	}
	if id != "" {
		docId, err := document.ParseId(id)
		if err != nil {
			return err
		}
		doc.Id = docId
	}
	if op > -1 {
		if id == "" && op != doc.Operation {
			return fmt.Errorf("wanted document operation is %s, but JSON file specifies %s", op, doc.Operation)
		}
		doc.Operation = op
	}
	if doc.Body != nil {
		service.CurlWriter.InputFile = filename
	}
	result := client.Send(doc)
	return printResult(cli, operationResult(false, doc, service, result), false)
}

func readDocuments(ids []string, timeoutSecs int, waiter *Waiter, printCurl bool, cli *CLI, fieldSet string, headers []string, ignoreNotFound bool) error {
	parsedIds := make([]document.Id, 0, len(ids))
	for _, id := range ids {
		parsedId, err := document.ParseId(id)
		if err != nil {
			return err
		}
		parsedIds = append(parsedIds, parsedId)
	}

	client, service, err := documentClient(cli, timeoutSecs, waiter, printCurl, headers)
	if err != nil {
		return err
	}

	for _, docId := range parsedIds {
		result := client.Get(docId, fieldSet)
		if err := printResult(cli, operationResult(true, document.Document{Id: docId}, service, result), true); err != nil {
			ignoreErr := ignoreNotFound && result.HTTPStatus == 404
			if !ignoreErr {
				return err
			}
		}
	}

	return nil
}

func operationResult(read bool, doc document.Document, service *vespa.Service, result document.Result) OperationResult {
	if result.Err != nil {
		return Failure(result.Err.Error())
	}
	bodyReader := bytes.NewReader(result.Body)
	if result.HTTPStatus == 200 {
		if read {
			return SuccessWithPayload("Read "+doc.Id.String(), ioutil.ReaderToJSON(bodyReader))
		} else {
			return Success(doc.Operation.String() + " " + doc.Id.String())
		}
	}
	if result.HTTPStatus/100 == 4 {
		return FailureWithPayload("Invalid document operation: Status "+strconv.Itoa(result.HTTPStatus), ioutil.ReaderToJSON(bodyReader))
	}
	return FailureWithPayload(service.Description()+" at "+service.BaseURL+": Status "+strconv.Itoa(result.HTTPStatus), ioutil.ReaderToJSON(bodyReader))
}

func newDocumentCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
		waitSecs    int
		headers     []string
	)
	cmd := &cobra.Command{
		Use:   "document json-file",
		Short: "Issue a single document operation to Vespa",
		Long: `Issue a single document operation to Vespa.

The operation must be on the format documented in
https://docs.vespa.ai/en/reference/document-json-format.html#document-operations

When this returns successfully, the document is guaranteed to be visible in any
subsequent get or query operation.

To feed with high throughput, https://docs.vespa.ai/en/reference/vespa-cli/vespa_feed.html
should be used instead of this.`,
		Example:           `$ vespa document src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			return sendOperation(-1, args, timeoutSecs, waiter, printCurl, cli, headers)
		},
	}
	addDocumentFlags(cli, cmd, &printCurl, &timeoutSecs, &waitSecs, &headers)
	return cmd
}

func newDocumentPutCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
		waitSecs    int
		headers     []string
	)
	cmd := &cobra.Command{
		Use:   "put [id] json-file",
		Short: "Writes a document to Vespa",
		Long: `Writes the document in the given file to Vespa.
If the document already exists, all its values will be replaced by this document.
If the document id is specified both as an argument and in the file the argument takes precedence.

If json-file is a single dash ('-'), the document will be read from standard input.
`,
		Args: cobra.RangeArgs(1, 2),
		Example: `$ vespa document put src/test/resources/A-Head-Full-of-Dreams.json
$ vespa document put id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			return sendOperation(document.OperationPut, args, timeoutSecs, waiter, printCurl, cli, headers)
		},
	}
	addDocumentFlags(cli, cmd, &printCurl, &timeoutSecs, &waitSecs, &headers)
	return cmd
}

func newDocumentUpdateCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
		waitSecs    int
		headers     []string
	)
	cmd := &cobra.Command{
		Use:   "update [id] json-file",
		Short: "Modifies some fields of an existing document",
		Long: `Updates the values of the fields given in a json file as specified in the file.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
		Args: cobra.RangeArgs(1, 2),
		Example: `$ vespa document update src/test/resources/A-Head-Full-of-Dreams-Update.json
$ vespa document update id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			return sendOperation(document.OperationUpdate, args, timeoutSecs, waiter, printCurl, cli, headers)
		},
	}
	addDocumentFlags(cli, cmd, &printCurl, &timeoutSecs, &waitSecs, &headers)
	return cmd
}

func newDocumentRemoveCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
		waitSecs    int
		headers     []string
	)
	cmd := &cobra.Command{
		Use:   "remove id | json-file",
		Short: "Removes a document from Vespa",
		Long: `Removes the document specified either as a document id or given in the json file.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
		Args: cobra.ExactArgs(1),
		Example: `$ vespa document remove src/test/resources/A-Head-Full-of-Dreams-Remove.json
$ vespa document remove id:mynamespace:music::a-head-full-of-dreams`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			if strings.HasPrefix(args[0], "id:") {
				client, service, err := documentClient(cli, timeoutSecs, waiter, printCurl, headers)
				if err != nil {
					return err
				}
				id, err := document.ParseId(args[0])
				if err != nil {
					return err
				}
				doc := document.Document{Id: id, Operation: document.OperationRemove}
				result := client.Send(doc)
				return printResult(cli, operationResult(false, doc, service, result), false)
			} else {
				return sendOperation(document.OperationRemove, args, timeoutSecs, waiter, printCurl, cli, headers)
			}
		},
	}
	addDocumentFlags(cli, cmd, &printCurl, &timeoutSecs, &waitSecs, &headers)
	return cmd
}

func newDocumentGetCmd(cli *CLI) *cobra.Command {
	var (
		printCurl      bool
		ignoreNotFound bool
		timeoutSecs    int
		waitSecs       int
		fieldSet       string
		headers        []string
	)
	cmd := &cobra.Command{
		Use:               "get id(s)",
		Short:             "Gets one or more documents",
		Args:              cobra.MinimumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Example: `$ vespa document get id:mynamespace:music::song-1
$ vespa document get id:mynamespace:music::song-1 id:mynamespace:music::song-2`,
		RunE: func(cmd *cobra.Command, args []string) error {
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			return readDocuments(args, timeoutSecs, waiter, printCurl, cli, fieldSet, headers, ignoreNotFound)
		},
	}
	cmd.Flags().StringVar(&fieldSet, "field-set", "", "Fields to include when reading document")
	cmd.Flags().BoolVar(&ignoreNotFound, "ignore-missing", false, "Do not treat non-existent document as an error")
	addDocumentFlags(cli, cmd, &printCurl, &timeoutSecs, &waitSecs, &headers)
	return cmd
}

func documentService(cli *CLI, waiter *Waiter) (*vespa.Service, error) {
	target, err := cli.target(targetOptions{})
	if err != nil {
		return nil, err
	}
	authMethod := cli.selectAuthMethod()
	return waiter.ServiceWithAuthMethod(target, cli.config.cluster(), authMethod)
}

func printResult(cli *CLI, result OperationResult, payloadOnlyOnSuccess bool) error {
	out := cli.Stdout
	if !result.Success {
		out = cli.Stderr
	}

	if !result.Success {
		fmt.Fprintln(out, color.RedString("Error:"), result.Message)
	} else if !payloadOnlyOnSuccess || result.Payload == "" {
		fmt.Fprintln(out, color.GreenString("Success:"), result.Message)
	}

	if result.Detail != "" {
		fmt.Fprintln(out, color.YellowString(result.Detail))
	}

	if result.Payload != "" {
		if !payloadOnlyOnSuccess {
			fmt.Fprintln(out)
		}
		fmt.Fprintln(out, result.Payload)
	}
	if !result.Success {
		err := errHint(fmt.Errorf("document operation failed"))
		err.quiet = true
		return err
	}
	return nil
}
