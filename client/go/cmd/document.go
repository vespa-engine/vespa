// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
	"bytes"
	"encoding/json"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/util"
	"github.com/vespa-engine/vespa/vespa"
)

func init() {
	rootCmd.AddCommand(documentCmd)
	documentCmd.AddCommand(documentPostCmd)
	documentCmd.AddCommand(documentGetCmd)
}

var documentCmd = &cobra.Command{
	Use:   "document",
	Short: "Issue document operations",
	Example: `$ vespa document src/test/resources/A-Head-Full-of-Dreams.json
# (short-hand for vespa document post)`,
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		post("", args[0])
	},
}

var documentPostCmd = &cobra.Command{
	Use:   "post",
	Short: "Posts the document in the given file",
	Args:  cobra.RangeArgs(1, 2),
	Example: `$ vespa document post src/test/resources/A-Head-Full-of-Dreams.json
$ vespa document post id:mynamespace:music::a-head-full-of-dreams src/test/resources/A-Head-Full-of-Dreams.json`,
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 1 {
			post("", args[0])
		} else {
			post(args[0], args[1])
		}
	},
}

var documentGetCmd = &cobra.Command{
	Use:   "get",
	Short: "Gets a document",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		get(args[0])
	},
}

func get(documentId string) {
	// TODO
}

func post(documentId string, jsonFile string) {
	header := http.Header{}
	header.Add("Content-Type", "application/json")

	fileReader, fileError := os.Open(jsonFile)
	if fileError != nil {
		log.Print(color.Red("Error: "), "Could not open file '", color.Cyan(jsonFile), "'")
		log.Print(color.Brown(fileError))
		return
	}

	documentData := util.ReaderToBytes(fileReader)

	if documentId == "" {
		var doc map[string]interface{}
		json.Unmarshal(documentData, &doc)
		if doc["put"] != nil {
			documentId = doc["put"].(string) // document feeder format
		} else {
			log.Print(color.Red("Error: "), "No document id given neither as argument or as a 'put' key in the json file")
			return
		}
	}

	documentPath, documentPathError := vespa.IdToURLPath(documentId)
	if documentPathError != nil {
		log.Print(color.Red("Error: "), "Invalid document id '", color.Red(documentId), "': ", documentPathError)
		return
	}

	url, urlParseError := url.Parse(documentTarget() + "/document/v1/" + documentPath)
	if urlParseError != nil {
		log.Print(color.Red("Error: "), "Invalid request path: '", color.Red(documentTarget()+"/document/v1/"+documentPath), "': ", urlParseError)
		return
	}

	request := &http.Request{
		URL:    url,
		Method: "POST",
		Header: header,
		Body:   ioutil.NopCloser(bytes.NewReader(documentData)),
	}
	serviceDescription := "Container (document API)"
	response, err := util.HttpDo(request, time.Second*60, serviceDescription)
	if response == nil {
		log.Print(color.Red("Error: "), "Request failed:", err)
		return
	}

	defer response.Body.Close()
	if response.StatusCode == 200 {
		log.Print(color.Green(documentId))
	} else if response.StatusCode/100 == 4 {
		log.Print(color.Red("Error: "), "Invalid document: ", response.Status, "\n\n")
		log.Print(util.ReaderToJSON(response.Body))
	} else {
		log.Print(color.Red("Error: "), serviceDescription, " at ", color.Cyan(request.URL.Host), ": ", response.Status, "\n\n")
		log.Print(util.ReaderToJSON(response.Body))
	}
}
