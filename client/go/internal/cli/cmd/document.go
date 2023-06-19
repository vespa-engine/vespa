// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/curl"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/document"
)

func addDocumentFlags(cmd *cobra.Command, printCurl *bool, timeoutSecs *int) {
	cmd.PersistentFlags().BoolVarP(printCurl, "verbose", "v", false, "Print the equivalent curl command for the document operation")
	cmd.PersistentFlags().IntVarP(timeoutSecs, "timeout", "T", 60, "Timeout for the document request in seconds")
}

type serviceWithCurl struct {
	curlCmdWriter io.Writer
	bodyFile      string
	service       *vespa.Service
}

func (s *serviceWithCurl) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	cmd, err := curl.RawArgs(request.URL.String())
	if err != nil {
		return nil, err
	}
	cmd.Method = request.Method
	for k, vs := range request.Header {
		for _, v := range vs {
			cmd.Header(k, v)
		}
	}
	if s.bodyFile != "" {
		cmd.WithBodyFile(s.bodyFile)
	}
	cmd.Certificate = s.service.TLSOptions.CertificateFile
	cmd.PrivateKey = s.service.TLSOptions.PrivateKeyFile
	out := cmd.String() + "\n"
	if _, err := io.WriteString(s.curlCmdWriter, out); err != nil {
		return nil, err
	}
	return s.service.Do(request, timeout)
}

func documentClient(cli *CLI, timeoutSecs int, printCurl bool) (*document.Client, *serviceWithCurl, error) {
	docService, err := documentService(cli)
	if err != nil {
		return nil, nil, err
	}
	service := &serviceWithCurl{curlCmdWriter: io.Discard, service: docService}
	if printCurl {
		service.curlCmdWriter = cli.Stderr
	}
	client, err := document.NewClient(document.ClientOptions{
		Compression: document.CompressionAuto,
		Timeout:     time.Duration(timeoutSecs) * time.Second,
		BaseURL:     docService.BaseURL,
		NowFunc:     time.Now,
	}, []util.HTTPClient{service})
	if err != nil {
		return nil, nil, err
	}
	return client, service, nil
}

func sendOperation(op document.Operation, args []string, timeoutSecs int, printCurl bool, cli *CLI) error {
	client, service, err := documentClient(cli, timeoutSecs, printCurl)
	if err != nil {
		return err
	}
	id := ""
	filename := args[0]
	if len(args) > 1 {
		id = args[0]
		filename = args[1]
	}
	f, err := os.Open(filename)
	if err != nil {
		return err
	}
	defer f.Close()
	doc, err := document.NewDecoder(f).Decode()
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
		service.bodyFile = f.Name()
	}
	result := client.Send(doc)
	return printResult(cli, operationResult(false, doc, service.service, result), false)
}

func readDocument(id string, timeoutSecs int, printCurl bool, cli *CLI) error {
	client, service, err := documentClient(cli, timeoutSecs, printCurl)
	if err != nil {
		return err
	}
	docId, err := document.ParseId(id)
	if err != nil {
		return err
	}
	result := client.Get(docId)
	return printResult(cli, operationResult(true, document.Document{Id: docId}, service.service, result), true)
}

func operationResult(read bool, doc document.Document, service *vespa.Service, result document.Result) util.OperationResult {
	if result.Err != nil {
		return util.Failure(result.Err.Error())
	}
	bodyReader := bytes.NewReader(result.Body)
	if result.HTTPStatus == 200 {
		if read {
			return util.SuccessWithPayload("Read "+doc.Id.String(), util.ReaderToJSON(bodyReader))
		} else {
			return util.Success(doc.Operation.String() + " " + doc.Id.String())
		}
	}
	if result.HTTPStatus/100 == 4 {
		return util.FailureWithPayload("Invalid document operation: Status "+strconv.Itoa(result.HTTPStatus), util.ReaderToJSON(bodyReader))
	}
	return util.FailureWithPayload(service.Description()+" at "+service.BaseURL+": Status "+strconv.Itoa(result.HTTPStatus), util.ReaderToJSON(bodyReader))
}

func newDocumentCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:   "document json-file",
		Short: "Issue a single document operation to Vespa",
		Long: `Issue a single document operation to Vespa.

The operation must be on the format documented in
https://docs.vespa.ai/en/reference/document-json-format.html#document-operations

When this returns successfully, the document is guaranteed to be visible in any
subsequent get or query operation.

To feed with high throughput, https://docs.vespa.ai/en/vespa-feed-client.html
should be used instead of this.`,
		Example:           `$ vespa document src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return sendOperation(-1, args, timeoutSecs, printCurl, cli)
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentPutCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:   "put [id] json-file",
		Short: "Writes a document to Vespa",
		Long: `Writes the document in the given file to Vespa.
If the document already exists, all its values will be replaced by this document.
If the document id is specified both as an argument and in the file the argument takes precedence.`,
		Args: cobra.RangeArgs(1, 2),
		Example: `$ vespa document put src/test/resources/A-Head-Full-of-Dreams.json
$ vespa document put id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return sendOperation(document.OperationPut, args, timeoutSecs, printCurl, cli)
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentUpdateCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
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
			return sendOperation(document.OperationUpdate, args, timeoutSecs, printCurl, cli)
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentRemoveCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
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
			if strings.HasPrefix(args[0], "id:") {
				client, service, err := documentClient(cli, timeoutSecs, printCurl)
				if err != nil {
					return err
				}
				id, err := document.ParseId(args[0])
				if err != nil {
					return err
				}
				doc := document.Document{Id: id, Operation: document.OperationRemove}
				result := client.Send(doc)
				return printResult(cli, operationResult(false, doc, service.service, result), false)
			} else {
				return sendOperation(document.OperationRemove, args, timeoutSecs, printCurl, cli)
			}
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func newDocumentGetCmd(cli *CLI) *cobra.Command {
	var (
		printCurl   bool
		timeoutSecs int
	)
	cmd := &cobra.Command{
		Use:               "get id",
		Short:             "Gets a document",
		Args:              cobra.ExactArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Example:           `$ vespa document get id:mynamespace:music::a-head-full-of-dreams`,
		RunE: func(cmd *cobra.Command, args []string) error {
			return readDocument(args[0], timeoutSecs, printCurl, cli)
		},
	}
	addDocumentFlags(cmd, &printCurl, &timeoutSecs)
	return cmd
}

func documentService(cli *CLI) (*vespa.Service, error) {
	target, err := cli.target(targetOptions{})
	if err != nil {
		return nil, err
	}
	return cli.service(target, vespa.DocumentService, 0, cli.config.cluster())
}

func printResult(cli *CLI, result util.OperationResult, payloadOnlyOnSuccess bool) error {
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
