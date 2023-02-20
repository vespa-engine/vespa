// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa visit command
// Author: arnej

package cmd

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

type visitArgs struct {
	contentCluster string
	fieldSet       string
	selection      string
	makeFeed       bool
	jsonLines      bool
	pretty         bool
	debugMode      bool
	chunkCount     int
	cli            *CLI
}

func (v *visitArgs) writeBytes(b []byte) {
	v.cli.Stdout.Write(b)
}

func (v *visitArgs) writeString(s string) {
	v.writeBytes([]byte(s))
}

func (v *visitArgs) debugPrint(s string) {
	if v.debugMode {
		v.cli.printDebug(s)
	}
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
	for _, value := range documents {
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
		if comma {
			v.writeString(",\n")
		} else {
			v.writeString("\n")
		}
	}
}

var totalDocCount int

func newVisitCmd(cli *CLI) *cobra.Command {
	var (
		vArgs visitArgs
	)
	cmd := &cobra.Command{
		Use:   "visit",
		Short: "Visit and print all documents in a vespa cluster",
		Long: `Run visiting to retrieve all documents.

By default prints each document received on its own line (JSON-L format).
`,
		Example: `$ vespa visit # get documents from any cluster
$ vespa visit --content-cluster search # get documents from cluster named "search"
`,
		Args:              cobra.MaximumNArgs(0),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			vArgs.cli = cli
			service, err := documentService(cli)
			if err != nil {
				return err
			}
			result := probeHandler(service, cli)
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
	cmd.Flags().StringVar(&vArgs.selection, "selection", "", `select subset of cluster`)
	cmd.Flags().BoolVar(&vArgs.debugMode, "debug-mode", false, `print debugging output`)
	cmd.Flags().BoolVar(&vArgs.jsonLines, "json-lines", true, `output documents as JSON lines`)
	cmd.Flags().BoolVar(&vArgs.makeFeed, "make-feed", false, `output JSON array suitable for vespa-feeder`)
	cmd.Flags().BoolVar(&vArgs.pretty, "pretty-json", false, `format pretty JSON`)
	cmd.Flags().IntVar(&vArgs.chunkCount, "chunk-count", 1000, `chunk by count`)
	return cmd
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

func probeHandler(service *vespa.Service, cli *CLI) (res util.OperationResult) {
	urlPath := service.BaseURL + "/"
	url, urlParseError := url.Parse(urlPath)
	if urlParseError != nil {
		return util.Failure("Invalid request path: '" + urlPath + "': " + urlParseError.Error())
	}
	request := &http.Request{
		URL:    url,
		Method: "GET",
	}
	timeout := time.Duration(90) * time.Second
	response, err := service.Do(request, timeout)
	if err != nil {
		return util.Failure("Request failed: " + err.Error())
	}
	defer response.Body.Close()
	if response.StatusCode == 200 {
		handlersInfo, err := parseHandlersOutput(response.Body)
		if err != nil || len(handlersInfo.Handlers) == 0 {
			cli.printWarning("Could not parse JSON response from"+urlPath, err.Error())
			return util.Failure("Bad endpoint")
		}
		for _, h := range handlersInfo.Handlers {
			if strings.HasSuffix(h.HandlerClass, "DocumentV1ApiHandler") {
				for _, binding := range h.ServerBindings {
					if strings.Contains(binding, "/document/v1/") {
						return util.Success("handler OK")
					}
				}
				w := fmt.Sprintf("expected /document/v1/ binding, but got: %v", h.ServerBindings)
				cli.printWarning(w)
			}
		}
		cli.printWarning("Missing /document/v1/ API; add <document-api /> to the container cluster delcaration in services.xml")
		return util.Failure("Missing /document/v1 API")
	} else {
		return util.FailureWithPayload(service.Description()+" at "+request.URL.Host+": "+response.Status, util.ReaderToJSON(response.Body))
	}
}

func visitClusters(vArgs *visitArgs, service *vespa.Service) (res util.OperationResult) {
	clusters := []string{
		vArgs.contentCluster,
	}
	if vArgs.contentCluster == "*" {
		clusters = probeVisit(vArgs, service)
	}
	if vArgs.makeFeed {
		vArgs.writeString("[\n")
	}
	for _, c := range clusters {
		vArgs.contentCluster = c
		res = runVisit(vArgs, service)
		if !res.Success {
			return res
		}
		vArgs.debugPrint("Success: " + res.Message)
	}
	if vArgs.makeFeed {
		vArgs.writeString("{}\n]\n")
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

func runVisit(vArgs *visitArgs, service *vespa.Service) (res util.OperationResult) {
	vArgs.debugPrint(fmt.Sprintf("trying to visit: '%s'", vArgs.contentCluster))
	var totalDocuments int = 0
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

func runOneVisit(vArgs *visitArgs, service *vespa.Service, contToken string) (*VespaVisitOutput, util.OperationResult) {
	urlPath := service.BaseURL + "/document/v1/?cluster=" + quoteArgForUrl(vArgs.contentCluster)
	if vArgs.fieldSet != "" {
		urlPath = urlPath + "&fieldSet=" + quoteArgForUrl(vArgs.fieldSet)
	}
	if vArgs.selection != "" {
		urlPath = urlPath + "&selection=" + quoteArgForUrl(vArgs.selection)
	}
	if contToken != "" {
		urlPath = urlPath + "&continuation=" + contToken
	}
	if vArgs.chunkCount > 0 {
		urlPath = urlPath + fmt.Sprintf("&wantedDocumentCount=%d", vArgs.chunkCount)
	}
	url, urlParseError := url.Parse(urlPath)
	if urlParseError != nil {
		return nil, util.Failure("Invalid request path: '" + urlPath + "': " + urlParseError.Error())
	}
	request := &http.Request{
		URL:    url,
		Method: "GET",
	}
	timeout := time.Duration(900) * time.Second
	response, err := service.Do(request, timeout)
	if err != nil {
		return nil, util.Failure("Request failed: " + err.Error())
	}
	defer response.Body.Close()
	vvo, err := parseVisitOutput(response.Body)
	if response.StatusCode == 200 {
		if err == nil {
			totalDocCount += vvo.DocumentCount
			if vvo.DocumentCount != len(vvo.Documents) {
				vArgs.cli.printWarning(fmt.Sprintf("Inconsistent contents from: %v", url))
				vArgs.cli.printWarning(fmt.Sprintf("claimed count: %d", vvo.DocumentCount))
				vArgs.cli.printWarning(fmt.Sprintf("document blobs: %d", len(vvo.Documents)))
				return nil, util.Failure("Inconsistent contents from document API")
			}
			return vvo, util.Success("visited " + vArgs.contentCluster)
		} else {
			return nil, util.Failure("error reading response: " + err.Error())
		}
	} else if response.StatusCode/100 == 4 {
		return vvo, util.FailureWithPayload("Invalid document operation: "+response.Status, util.ReaderToJSON(response.Body))
	} else {
		return vvo, util.FailureWithPayload(service.Description()+" at "+request.URL.Host+": "+response.Status, util.ReaderToJSON(response.Body))
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
