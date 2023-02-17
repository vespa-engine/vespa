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
	"os"
	"strings"
	"time"

	"github.com/fatih/color"
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
	quietMode      bool
	chunkCount     int
}

var totalDocCount int

func newVisitCmd(cli *CLI) *cobra.Command {
	var (
		vArgs visitArgs
	)
	cmd := &cobra.Command{
		Use:   "visit",
		Short: "Visit all documents in a content cluster",
		Long: `Run visiting of a content cluster to retrieve all documents.

more explanation here
even more explanation here
`,
		Example: `$ vespa visit # get documents from any cluster
$ vespa visit --content-cluster search # get documents from cluster named "search"
`,
		Args:              cobra.MaximumNArgs(0),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			vArgs.quietMode = cli.config.isQuiet()
			service, err := documentService(cli)
			if err != nil {
				return err
			}
			result := probeHandler(service)
			if result.Success {
				result = visitClusters(vArgs, service)
			}
			if !result.Success {
				return fmt.Errorf("visit failed: %s", result.Message)
			}
			if !vArgs.quietMode {
				fmt.Fprintln(os.Stderr, "[debug] sum of 'documentCount':", totalDocCount)
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&vArgs.contentCluster, "content-cluster", "*", `Which content cluster to visit documents from`)
	cmd.Flags().StringVar(&vArgs.fieldSet, "field-set", "", `Which fieldset to ask for`)
	cmd.Flags().StringVar(&vArgs.selection, "selection", "", `select subset of cluster`)
	cmd.Flags().BoolVar(&vArgs.jsonLines, "json-lines", false, `output documents as JSON lines`)
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

func probeHandler(service *vespa.Service) (res util.OperationResult) {
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
			fmt.Fprintln(os.Stderr, "Could not parse JSON response from", urlPath, err)
			return util.Failure("Bad endpoint")
		}
		for _, h := range handlersInfo.Handlers {
			if strings.HasSuffix(h.HandlerClass, "DocumentV1ApiHandler") {
				for _, binding := range h.ServerBindings {
					if strings.Contains(binding, "/document/v1/") {
						return util.Success("handler OK")
					}
				}
				fmt.Fprintln(os.Stderr, "expected /document/v1/ binding, but got:", h.ServerBindings)
			}
		}
		fmt.Fprintln(os.Stderr, "Missing /document/v1/ API; add <document-api /> to the container cluster delcaration in services.xml")
		return util.Failure("Missing /document/v1 API")
	} else {
		return util.FailureWithPayload(service.Description()+" at "+request.URL.Host+": "+response.Status, util.ReaderToJSON(response.Body))
	}
}

func visitClusters(vArgs visitArgs, service *vespa.Service) (res util.OperationResult) {
	clusters := []string{
		vArgs.contentCluster,
	}
	if vArgs.contentCluster == "*" {
		clusters = probeVisit(vArgs, service)
	}
	if vArgs.makeFeed {
		fmt.Printf("[")
	}
	for _, c := range clusters {
		vArgs.contentCluster = c
		res = runVisit(vArgs, service)
		if !res.Success {
			return res
		}
		if !vArgs.quietMode {
			fmt.Fprintln(os.Stderr, color.GreenString("Success:"), res.Message)
		}
	}
	if vArgs.makeFeed {
		fmt.Println("{}\n]")
	}
	return res
}

func probeVisit(vArgs visitArgs, service *vespa.Service) []string {
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

func runVisit(vArgs visitArgs, service *vespa.Service) (res util.OperationResult) {
	if !vArgs.quietMode {
		fmt.Fprintf(os.Stderr, "[debug] trying to visit: '%s'\n", vArgs.contentCluster)
	}
	var totalDocuments int = 0
	var continuationToken string
	for {
		var vvo *VespaVisitOutput
		vvo, res = runOneVisit(vArgs, service, continuationToken)
		if !res.Success {
			if vvo != nil && vvo.ErrorMsg != "" {
				fmt.Fprintln(os.Stderr, vvo.ErrorMsg)
			}
			return res
		}
		if vArgs.makeFeed {
			dumpDocuments(vvo.Documents, true, vArgs.pretty)
		} else if vArgs.jsonLines {
			dumpDocuments(vvo.Documents, false, vArgs.pretty)
		}
		if !vArgs.quietMode {
			fmt.Fprintln(os.Stderr, "[debug] got", len(vvo.Documents), "documents")
		}
		totalDocuments += len(vvo.Documents)
		continuationToken = vvo.Continuation
		if continuationToken == "" {
			break
		}
	}
	res.Message = fmt.Sprintf("%s [%d documents visited]", res.Message, totalDocuments)
	return
}

func runOneVisit(vArgs visitArgs, service *vespa.Service, contToken string) (*VespaVisitOutput, util.OperationResult) {
	urlPath := service.BaseURL + "/document/v1/?cluster=" + vArgs.contentCluster
	if vArgs.fieldSet != "" {
		urlPath = urlPath + "&fieldSet=" + vArgs.fieldSet
	}
	if vArgs.selection != "" {
		urlPath = urlPath + "&selection=" + vArgs.selection
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
				fmt.Fprintln(os.Stderr, "Inconsistent contents from:", url)
				fmt.Fprintln(os.Stderr, "claimed count: ", vvo.DocumentCount)
				fmt.Fprintln(os.Stderr, "document blobs: ", len(vvo.Documents))
				// return nil, util.Failure("Inconsistent contents from document API")
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
		fmt.Fprintln(os.Stderr, "could not decode JSON, error:", err)
		return nil, err
	}
	return &parsedJson, nil
}

func dumpDocuments(documents []DocumentBlob, comma, pretty bool) {
	for _, value := range documents {
		if pretty {
			var prettyJSON bytes.Buffer
			parseError := json.Indent(&prettyJSON, value.blob, "", "    ")
			if parseError != nil {
				os.Stdout.Write(value.blob)
			} else {
				os.Stdout.Write(prettyJSON.Bytes())
			}
		} else {
			os.Stdout.Write(value.blob)
		}
		if comma {
			fmt.Printf(",\n")
		} else {
			fmt.Printf("\n")
		}
	}
}
