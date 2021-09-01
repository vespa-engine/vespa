// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document API client
// Author: bratseth

package vespa

import (
	"bytes"
	"encoding/json"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"time"

	"github.com/vespa-engine/vespa/util"
)

func Get(documentId string, target string) *util.OperationResult {
	documentPath, documentPathError := IdToURLPath(documentId)
	if documentPathError != nil {
		return util.Failure("Invalid document id '" + documentId + "': " + documentPathError.Error())
	}

	url, urlParseError := url.Parse(target + "/document/v1/" + documentPath)
	if urlParseError != nil {
		return util.Failure("Invalid request path: '" + target + "/document/v1/" + documentPath + "': " + urlParseError.Error())
	}

	request := &http.Request{
		URL:    url,
		Method: "GET",
	}
	serviceDescription := "Container (document API)"
	response, err := util.HttpDo(request, time.Second*60, serviceDescription)
	if response == nil {
		return util.Failure("Request failed: " + err.Error())
	}

	defer response.Body.Close()
	if response.StatusCode == 200 {
		return util.SuccessWithPayload("Read "+documentId, util.ReaderToJSON(response.Body))
	} else if response.StatusCode/100 == 4 {
		return util.FailureWithPayload("Invalid document: "+response.Status, util.ReaderToJSON(response.Body))
	} else {
		return util.FailureWithPayload(serviceDescription+" at "+request.URL.Host+": "+response.Status, util.ReaderToJSON(response.Body))
	}
}

func Post(documentId string, jsonFile string, target string) *util.OperationResult {
	header := http.Header{}
	header.Add("Content-Type", "application/json")

	fileReader, fileError := os.Open(jsonFile)
	if fileError != nil {
		return util.FailureWithDetail("Could not open file '"+jsonFile+"'", fileError.Error())
	}

	documentData := util.ReaderToBytes(fileReader)

	if documentId == "" {
		var doc map[string]interface{}
		json.Unmarshal(documentData, &doc)
		if doc["put"] != nil {
			documentId = doc["put"].(string) // document feeder format
		} else {
			return util.Failure("No document id given neither as argument or as a 'put' key in the json file")
		}
	}

	documentPath, documentPathError := IdToURLPath(documentId)
	if documentPathError != nil {
		return util.Failure("Invalid document id '" + documentId + "': " + documentPathError.Error())
	}

	url, urlParseError := url.Parse(target + "/document/v1/" + documentPath)
	if urlParseError != nil {
		return util.Failure("Invalid request path: '" + target + "/document/v1/" + documentPath + "': " + urlParseError.Error())
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
		return util.Failure("Request failed: " + err.Error())
	}

	defer response.Body.Close()
	if response.StatusCode == 200 {
		return util.Success("Wrote " + documentId)
	} else if response.StatusCode/100 == 4 {
		return util.FailureWithPayload("Invalid document: "+response.Status, util.ReaderToJSON(response.Body))
	} else {
		return util.FailureWithPayload(serviceDescription+" at "+request.URL.Host+": "+response.Status, util.ReaderToJSON(response.Body))
	}
}
