// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa visit command
// Author: arnej

package cmd

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/rand/v2"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

type visitArgs struct {
	contentCluster string
	fieldSet       string
	selection      string
	makeFeed       bool
	jsonLines      bool
	pretty         bool
	chunkCount     int
	from           string
	to             string
	slices         int
	sliceId        int
	bucketSpace    string
	bucketSpaces   []string
	waitSecs       int
	verbose        bool
	headers        []string
	stream         bool

	cli    *CLI
	header http.Header
}

func (v *visitArgs) writeBytes(b []byte) {
	v.cli.Stdout.Write(b)
}

func (v *visitArgs) writeString(s string) {
	v.writeBytes([]byte(s))
}

func (v *visitArgs) debugPrint(s string) {
	v.cli.printDebug(s)
}

func (v *visitArgs) dumpDocuments(documents []DocumentBlob) {
	comma := false
	pretty := false
	if v.makeFeed {
		comma = true
		pretty = v.pretty
	} else if !v.jsonLines {
		return
	}
	for i, value := range documents {
		if pretty {
			var prettyJSON bytes.Buffer
			parseError := json.Indent(&prettyJSON, value.blob, "", "    ")
			if parseError != nil {
				v.writeBytes(value.blob)
			} else {
				v.writeBytes(prettyJSON.Bytes())
			}
		} else {
			v.writeBytes(value.blob)
		}
		var lastDocument = i == (len(documents) - 1)
		if comma && !lastDocument {
			v.writeString(",\n")
		} else {
			v.writeString("\n")
		}
	}
}

var totalDocCount int

func newVisitCmd(cli *CLI) *cobra.Command {
	var vArgs visitArgs
	cmd := &cobra.Command{
		Use:   "visit",
		Short: "Retrieve and print all documents from Vespa",
		Long: `Retrieve and print all documents from Vespa.

By default, prints each document received on its own line (JSONL format).
`,
		Example: `$ vespa visit # get documents from any cluster
$ vespa visit --content-cluster search # get documents from cluster named "search"
$ vespa visit --field-set "[id]" # list document IDs
`,
		Args:              cobra.MaximumNArgs(0),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			vArgs.cli = cli
			result := checkArguments(vArgs)
			if !result.Success {
				return fmt.Errorf("argument error: %s", result.Message)
			}
			header, err := httputil.ParseHeader(vArgs.headers)
			if err != nil {
				return err
			}
			vArgs.header = header
			waiter := cli.waiter(time.Duration(vArgs.waitSecs)*time.Second, cmd)
			service, err := documentService(cli, waiter)
			if err != nil {
				return err
			}
			if service.AuthMethod == "token" {
				err = cli.addBearerToken(&header)
				if err != nil {
					return err
				}
				service.TLSOptions.CertificateFile = ""
				service.TLSOptions.PrivateKeyFile = ""
			}
			if vArgs.verbose {
				service.CurlWriter = vespa.CurlWriter{Writer: cli.Stderr}
			}
			result = probeHandler(&vArgs, service, cli)
			if result.Success {
				result = visitClusters(&vArgs, service)
			}
			if !result.Success {
				return fmt.Errorf("visit failed: %s", result.Message)
			}
			vArgs.debugPrint(fmt.Sprintf("sum of 'documentCount': %d", totalDocCount))
			return nil
		},
	}
	cmd.Flags().StringVar(&vArgs.contentCluster, "content-cluster", "*", `Which content cluster to visit documents from`)
	cmd.Flags().StringVar(&vArgs.fieldSet, "field-set", "", `Which fieldset to ask for`)
	cmd.Flags().StringVar(&vArgs.selection, "selection", "", `Select subset of cluster`)
	cmd.Flags().BoolVar(&vArgs.jsonLines, "json-lines", true, `Output documents as JSON lines`)
	cmd.Flags().BoolVar(&vArgs.makeFeed, "make-feed", false, `Output JSON array suitable for vespa-feeder`)
	cmd.Flags().BoolVar(&vArgs.pretty, "pretty-json", false, `Format pretty JSON`)
	cmd.Flags().IntVar(&vArgs.chunkCount, "chunk-count", 1000, `Chunk by count`)
	cmd.Flags().StringVar(&vArgs.from, "from", "", `Timestamp to visit from, in seconds`)
	cmd.Flags().StringVar(&vArgs.to, "to", "", `Timestamp to visit up to, in seconds`)
	cmd.Flags().IntVar(&vArgs.sliceId, "slice-id", -1, `The number of the slice this visit invocation should fetch`)
	cmd.Flags().IntVar(&vArgs.slices, "slices", -1, `Split the document corpus into this number of independent slices`)
	cmd.Flags().StringSliceVar(&vArgs.bucketSpaces, "bucket-space", []string{"global", "default"}, `The "default" or "global" bucket space`)
	cmd.Flags().BoolVarP(&vArgs.verbose, "verbose", "v", false, `Print the equivalent curl command for the visit operation`)
	cmd.Flags().StringSliceVarP(&vArgs.headers, "header", "", nil, "Add a header to the HTTP request, on the format 'Header: Value'. This can be specified multiple times")
	cmd.Flags().BoolVar(&vArgs.stream, "stream", false, "Stream the HTTP responses")
	cli.bindWaitFlag(cmd, 0, &vArgs.waitSecs)
	return cmd
}

func getEpoch(timeStamp string) (int64, error) {
	t, err := strconv.ParseInt(timeStamp, 10, 64)
	if err != nil {
		t, err := time.Parse(time.RFC3339, timeStamp)
		if err != nil {
			return 0, err
		}
		return t.Unix(), nil
	}
	return t, nil
}

func checkArguments(vArgs visitArgs) (res OperationResult) {
	if vArgs.slices > 0 || vArgs.sliceId > -1 {
		if vArgs.slices <= 0 || vArgs.sliceId <= -1 {
			return Failure("Both 'slices' and 'slice-id' must be set")
		}
		if vArgs.sliceId >= vArgs.slices {
			return Failure("The 'slice-id' must be in range [0, slices)")
		}
	}
	// to and from will support RFC3339 format soon, add more validation then
	if vArgs.from != "" {
		_, err := getEpoch(vArgs.from)
		if err != nil {
			return Failure("Invalid 'from' argument: '" + vArgs.from + "': " + err.Error())
		}
	}
	if vArgs.to != "" {
		_, err := getEpoch(vArgs.to)
		if err != nil {
			return Failure("Invalid 'to' argument: '" + vArgs.to + "': " + err.Error())
		}
	}
	for _, b := range vArgs.bucketSpaces {
		switch b {
		case "default", "global":
			// Do nothing
		default:
			return Failure("Invalid 'bucket-space' argument '" + b + "', must be 'default' or 'global'")
		}
	}
	return Success("")
}

type HandlersInfo struct {
	Handlers []struct {
		HandlerId      string   `json:"id"`
		HandlerClass   string   `json:"class"`
		HandlerBundle  string   `json:"bundle"`
		ServerBindings []string `json:"serverBindings"`
	} `json:"handlers"`
}

func parseHandlersOutput(r io.Reader) (*HandlersInfo, error) {
	var handlersInfo HandlersInfo
	codec := json.NewDecoder(r)
	err := codec.Decode(&handlersInfo)
	return &handlersInfo, err
}

func probeHandler(vArgs *visitArgs, service *vespa.Service, cli *CLI) (res OperationResult) {
	urlPath := service.BaseURL + "/"
	url, urlParseError := url.Parse(urlPath)
	if urlParseError != nil {
		return Failure("Invalid request path: '" + urlPath + "': " + urlParseError.Error())
	}
	request := &http.Request{
		URL:    url,
		Method: "GET",
		Header: vArgs.header,
	}
	timeout := time.Duration(90) * time.Second
	const maxRetrySeconds = 15
	deadline := time.Now().Add(maxRetrySeconds * time.Second)
	retryStart := time.Now()
	var response *http.Response
	var err error
	inRetry := false
	for {
		response, err = service.Do(request, timeout)
		if err == nil {
			if inRetry {
				fmt.Fprintf(cli.Stderr, "\r\033[K")
			}
			break
		}
		if (errors.Is(err, io.EOF) || strings.Contains(err.Error(), "EOF")) && time.Now().Before(deadline) {
			inRetry = true
			for i := 3; i > 0; i-- {
				elapsed := int(time.Since(retryStart).Seconds())
				fmt.Fprintf(cli.Stderr, "\r\033[K  Got EOF, retrying in %ds... [%ds / %ds]", i, elapsed, maxRetrySeconds)
				time.Sleep(time.Second)
			}
			continue
		}
		if inRetry {
			fmt.Fprintf(cli.Stderr, "\n")
		}
		return Failure("Request failed: " + err.Error())
	}
	defer response.Body.Close()
	if response.StatusCode == 200 {
		handlersInfo, err := parseHandlersOutput(response.Body)
		if err != nil || len(handlersInfo.Handlers) == 0 {
			cli.printWarning("Could not parse JSON response from"+urlPath, err.Error())
			return Failure("Bad endpoint")
		}
		for _, h := range handlersInfo.Handlers {
			if strings.HasSuffix(h.HandlerClass, "DocumentV1ApiHandler") {
				for _, binding := range h.ServerBindings {
					if strings.Contains(binding, "/document/v1/") {
						return Success("handler OK")
					}
				}
				w := fmt.Sprintf("expected /document/v1/ binding, but got: %v", h.ServerBindings)
				cli.printWarning(w)
			}
		}
		cli.printWarning("Missing /document/v1/ API; add <document-api /> to the container cluster declaration in services.xml")
		return Failure("Missing /document/v1 API")
	} else {
		return FailureWithPayload(service.Description()+" at "+request.URL.Host+": "+response.Status, ioutil.ReaderToJSON(response.Body))
	}
}

func visitClusters(vArgs *visitArgs, service *vespa.Service) (res OperationResult) {
	clusters := []string{
		vArgs.contentCluster,
	}
	if vArgs.contentCluster == "*" {
		clusters = probeVisit(vArgs, service)
	}
	if vArgs.makeFeed {
		vArgs.writeString("[\n")
	}
	for _, b := range vArgs.bucketSpaces {
		for _, c := range clusters {
			vArgs.bucketSpace = b
			vArgs.contentCluster = c
			res = runVisit(vArgs, service)
			if !res.Success {
				return res
			}
			vArgs.debugPrint("Success: " + res.Message)
		}
	}
	if vArgs.makeFeed {
		vArgs.writeString("]\n")
	}
	return res
}

func probeVisit(vArgs *visitArgs, service *vespa.Service) []string {
	clusters := make([]string, 0, 3)
	vvo, _ := runOneVisit(vArgs, service, "")
	if vvo != nil {
		msg := vvo.ErrorMsg
		if strings.Contains(msg, "no content cluster '*'") {
			for idx, value := range strings.Split(msg, ",") {
				if idx > 0 {
					parts := strings.Split(value, "'")
					if len(parts) == 3 {
						clusters = append(clusters, parts[1])
					}
				}
			}
		}
	}
	return clusters
}

func runVisit(vArgs *visitArgs, service *vespa.Service) (res OperationResult) {
	vArgs.debugPrint(fmt.Sprintf("trying to visit: '%s'", vArgs.contentCluster))
	totalDocuments := 0
	const baseRetryBackoffMs = 200.0
	const maxRetryBackoffMs = 10_000.0 // Actually up to 15s, see below
	backoffBaselineMs := baseRetryBackoffMs
	var continuationToken string
	for {
		var vvo *VespaVisitOutput
		vvo, res = runOneVisit(vArgs, service, continuationToken)
		if !res.Success {
			if vvo != nil && vvo.ErrorMsg != "" {
				vArgs.cli.printWarning(vvo.ErrorMsg)
			}
			return res
		}
		if vvo == nil {
			// Success without visit output implies transparent retry with randomized delay.
			// Let randomized backoff be +/- 50% of the current backoff baseline, increasing
			// by 1.5x for each subsequent failure up to a hard limit of 10s. Since the max
			// real backoff is +50% this means we'll top out at 15 seconds of backoff.
			randomizedBackoffMs := int64((backoffBaselineMs * 0.5) + (rand.Float64() * backoffBaselineMs))
			vArgs.debugPrint(fmt.Sprintf("Transient overload; retrying in %d ms", randomizedBackoffMs))
			backoffBaselineMs = min(backoffBaselineMs*1.5, maxRetryBackoffMs)
			vArgs.cli.sleeper(time.Duration(randomizedBackoffMs) * time.Millisecond)
			continue
		}
		backoffBaselineMs = baseRetryBackoffMs
		vArgs.dumpDocuments(vvo.Documents)
		vArgs.debugPrint(fmt.Sprintf("got %d documents", len(vvo.Documents)))
		totalDocuments += len(vvo.Documents)
		continuationToken = vvo.Continuation
		if continuationToken == "" {
			break
		}
	}
	res.Message = fmt.Sprintf("%s [%d documents visited]", res.Message, totalDocuments)
	return
}

func quoteArgForUrl(arg string) string {
	var buf strings.Builder
	buf.Grow(len(arg))
	for _, r := range arg {
		switch {
		case 'a' <= r && r <= 'z':
			buf.WriteRune(r)
		case 'A' <= r && r <= 'Z':
			buf.WriteRune(r)
		case '0' <= r && r <= '9':
			buf.WriteRune(r)
		case r <= ' ' || r > '~':
			buf.WriteRune('+')
		default:
			s := fmt.Sprintf("%s%02X", "%", r)
			buf.WriteString(s)
		}
	}
	return buf.String()
}

func runOneVisit(vArgs *visitArgs, service *vespa.Service, contToken string) (*VespaVisitOutput, OperationResult) {
	urlPath := service.BaseURL + "/document/v1/?cluster=" + quoteArgForUrl(vArgs.contentCluster)
	if vArgs.fieldSet != "" {
		urlPath += "&fieldSet=" + quoteArgForUrl(vArgs.fieldSet)
	}
	if vArgs.selection != "" {
		urlPath += "&selection=" + quoteArgForUrl(vArgs.selection)
	}
	if contToken != "" {
		urlPath += "&continuation=" + contToken
	}
	if vArgs.chunkCount > 0 {
		urlPath += fmt.Sprintf("&wantedDocumentCount=%d", vArgs.chunkCount)
	}
	if vArgs.from != "" {
		fromSeconds, _ := getEpoch(vArgs.from)
		urlPath += fmt.Sprintf("&fromTimestamp=%d", fromSeconds*1000000)
	}
	if vArgs.to != "" {
		toSeconds, _ := getEpoch(vArgs.to)
		urlPath += fmt.Sprintf("&toTimestamp=%d", toSeconds*1000000)
	}
	if vArgs.slices > 0 {
		urlPath += fmt.Sprintf("&slices=%d&sliceId=%d", vArgs.slices, vArgs.sliceId)
	}
	if vArgs.bucketSpace != "" {
		urlPath += "&bucketSpace=" + vArgs.bucketSpace
	}
	urlPath += fmt.Sprintf("&stream=%t", vArgs.stream)
	url, urlParseError := url.Parse(urlPath)
	if urlParseError != nil {
		return nil, Failure("Invalid request path: '" + urlPath + "': " + urlParseError.Error())
	}
	request := &http.Request{
		URL:    url,
		Method: "GET",
		Header: vArgs.header,
	}
	timeout := time.Duration(900) * time.Second
	response, err := service.Do(request, timeout)
	if err != nil {
		return nil, Failure("Request failed: " + err.Error())
	}
	defer response.Body.Close()
	vvo, err := parseVisitOutput(response.Body)
	switch {
	case response.StatusCode == 200:
		if err == nil {
			totalDocCount += vvo.DocumentCount
			if vvo.DocumentCount != len(vvo.Documents) {
				vArgs.cli.printWarning(fmt.Sprintf("Inconsistent contents from: %v", url))
				vArgs.cli.printWarning(fmt.Sprintf("claimed count: %d", vvo.DocumentCount))
				vArgs.cli.printWarning(fmt.Sprintf("document blobs: %d", len(vvo.Documents)))
				return nil, Failure("Inconsistent contents from document API")
			}
			return vvo, Success("visited " + vArgs.contentCluster)
		} else {
			return nil, Failure("error reading response: " + err.Error())
		}
	case response.StatusCode == 429:
		return nil, Success("Transient overload")
	case response.StatusCode/100 == 4:
		return vvo, FailureWithPayload("Invalid document operation: "+response.Status, ioutil.ReaderToJSON(response.Body))
	default:
		return vvo, FailureWithPayload(service.Description()+" at "+request.URL.Host+": "+response.Status, ioutil.ReaderToJSON(response.Body))
	}
}

type DocumentBlob struct {
	blob []byte
}

func (d *DocumentBlob) UnmarshalJSON(data []byte) error {
	d.blob = make([]byte, len(data))
	copy(d.blob, data)
	return nil
}

func (d *DocumentBlob) MarshalJSON() ([]byte, error) {
	return d.blob, nil
}

type VespaVisitOutput struct {
	PathId        string         `json:"pathId"`
	Documents     []DocumentBlob `json:"documents"`
	DocumentCount int            `json:"documentCount"`
	Continuation  string         `json:"continuation"`
	ErrorMsg      string         `json:"message"`
}

func parseVisitOutput(r io.Reader) (*VespaVisitOutput, error) {
	codec := json.NewDecoder(r)
	var parsedJson VespaVisitOutput
	err := codec.Decode(&parsedJson)
	if err != nil {
		return nil, fmt.Errorf("could not decode JSON, error: %s", err.Error())
	}
	return &parsedJson, nil
}
