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
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/util"
)

func init() {
	rootCmd.AddCommand(documentCmd)
	documentCmd.AddCommand(documentPostCmd)
	documentCmd.AddCommand(documentGetCmd)
	addTargetFlag(documentCmd)
}

var documentCmd = &cobra.Command{
	Use:   "document",
	Short: "Issue document operations",
	Long:  `TODO: Example vespa document mynamespace/mydocumenttype/myid document.json`,
	// TODO: Check args
	Run: func(cmd *cobra.Command, args []string) {
		post("", args[0])
	},
}

var documentPostCmd = &cobra.Command{
	Use:   "post",
	Short: "Posts the document in the given file",
	Long:  `TODO`,
	// TODO: Check args
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
	Long:  `TODO`,
	// TODO: Check args
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
		log.Printf("Could not open file at %s: %s", color.Cyan(jsonFile), fileError)
		return
	}

	documentData := util.ReaderToBytes(fileReader)

	if documentId == "" {
		var doc map[string]interface{}
		json.Unmarshal(documentData, &doc)
		if doc["id"] != nil {
			documentId = doc["id"].(string)
		} else if doc["put"] != nil {
			documentId = doc["put"].(string) // document feeder format
		} else {
			log.Print("No document id given neither as argument or an 'id' key in the json file")
			return
		}
	}

	url, _ := url.Parse(documentTarget() + "/document/v1/" + documentId)

	request := &http.Request{
		URL:    url,
		Method: "POST",
		Header: header,
		Body:   ioutil.NopCloser(bytes.NewReader(documentData)),
	}
	serviceDescription := "Container (document API)"
	response, err := util.HttpDo(request, time.Second*60, serviceDescription)
	if response == nil {
		log.Print("Request failed: ", color.Red(err))
	}

	defer response.Body.Close()
	if response.StatusCode == 200 {
		log.Print(color.Green(documentId))
	} else if response.StatusCode/100 == 4 {
		log.Printf("Invalid document (%s):", color.Red(response.Status))
		log.Print(util.ReaderToJSON(response.Body))
	} else {
		log.Printf("Error from %s at %s (%s):", color.Cyan(strings.ToLower(serviceDescription)), color.Cyan(request.URL.Host), color.Red(response.Status))
		log.Print(util.ReaderToJSON(response.Body))
	}
}
