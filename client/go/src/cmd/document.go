// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document command
// author: bratseth

package cmd

import (
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "io/ioutil"
    "net/http"
    "net/url"
    "os"
    "strings"
    "time"
)

func init() {
    rootCmd.AddCommand(documentCmd)
    statusCmd.AddCommand(documentPutCmd)
    statusCmd.AddCommand(documentGetCmd)
}

var documentCmd = &cobra.Command{
    Use:   "document mynamespace/mydocumenttype/myid document.json",
    Short: "Issue document operations (put by default)",
    Long:  `TODO`,
    // TODO: Check args
    Run: func(cmd *cobra.Command, args []string) {
        put(args[0], args[1])
    },
}

var documentPutCmd = &cobra.Command{
    Use:   "put mynamespace/mydocumenttype/myid mydocument.json",
    Short: "Puts the document in the given file",
    Long:  `TODO`,
    // TODO: This crashes with the above
    // TODO: Extract document id from the content
    // TODO: Check args
    Run: func(cmd *cobra.Command, args []string) {
        put(args[0], args[1])
    },
}

var documentGetCmd = &cobra.Command{
    Use:   "get documentId",
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

func put(documentId string, jsonFile string) {
    url, _ := url.Parse(getTarget(documentContext).document + "/document/v1/" + documentId)

    header := http.Header{}
    header.Add("Content-Type", "application/json")

    fileReader, fileError := os.Open(jsonFile)
    if fileError != nil {
        utils.Error("Could not open file at " + jsonFile)
        utils.Detail(fileError.Error())
        return
    }

    request := &http.Request{
        URL: url,
        Method: "POST",
        Header: header,
        Body: ioutil.NopCloser(fileReader),
    }
    serviceDescription := "Container (document/v1 API)"
    response := utils.HttpDo(request, time.Second * 60, serviceDescription)
    defer response.Body.Close()
    if (response == nil) {
        return
    } else if response.StatusCode == 200 {
        utils.Success("Success") // TODO: Change to something including document id
    } else if response.StatusCode % 100 == 4 {
        utils.Error("Invalid document JSON")
        utils.Detail(response.Status)
        // TODO: Output error in body
    } else {
        utils.Error("Error from", strings.ToLower(serviceDescription), "at", request.URL.Host)
        utils.Detail("Response status:", response.Status)
    }
}