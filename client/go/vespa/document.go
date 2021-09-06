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

	"github.com/vespa-engine/vespa/client/go/util"
)

// Sends the operation given in the file
func Send(jsonFile string, service *Service) util.OperationResult {
	return sendOperation("", jsonFile, service, anyOperation)
}

func Put(documentId string, jsonFile string, service *Service) util.OperationResult {
	return sendOperation(documentId, jsonFile, service, putOperation)
}

func Update(documentId string, jsonFile string, service *Service) util.OperationResult {
	return sendOperation(documentId, jsonFile, service, updateOperation)
}

func RemoveId(documentId string, service *Service) util.OperationResult {
	return sendOperation(documentId, "", service, removeOperation)
}

func RemoveOperation(jsonFile string, service *Service) util.OperationResult {
	return sendOperation("", jsonFile, service, removeOperation)
}

const (
	anyOperation    string = "any"
	putOperation    string = "put"
	updateOperation string = "update"
	removeOperation string = "remove"
)

func sendOperation(documentId string, jsonFile string, service *Service, operation string) util.OperationResult {
	header := http.Header{}
	header.Add("Content-Type", "application/json")

	var documentData []byte
	if operation == "remove" && jsonFile == "" {
		documentData = []byte("{\n    \"remove\": \"" + documentId + "\"\n}\n")
	} else {
		fileReader, fileError := os.Open(jsonFile)
		if fileError != nil {
			return util.FailureWithDetail("Could not open file '"+jsonFile+"'", fileError.Error())
		}
		defer fileReader.Close()
		documentData = util.ReaderToBytes(fileReader)
	}

	var doc map[string]interface{}
	json.Unmarshal(documentData, &doc)

	operationInFile := operationIn(doc)
	if operation == anyOperation { // Operation is decided by file content
		operation = operationInFile
	} else if operationInFile != "" && operationInFile != operation { // Otherwise operation must match
		return util.Failure("Wanted document operation is " + operation + " but the JSON file specifies " + operationInFile)
	}

	if documentId == "" { // Document id is decided by file content
		if doc[operation] == nil {
			return util.Failure("No document id given neither as argument or as a '" + operation + "' key in the json file")
		}
		documentId = doc[operation].(string) // document feeder format
	}

	documentPath, documentPathError := IdToURLPath(documentId)
	if documentPathError != nil {
		return util.Failure("Invalid document id '" + documentId + "': " + documentPathError.Error())
	}

	url, urlParseError := url.Parse(service.BaseURL + "/document/v1/" + documentPath)
	if urlParseError != nil {
		return util.Failure("Invalid request path: '" + service.BaseURL + "/document/v1/" + documentPath + "': " + urlParseError.Error())
	}

	request := &http.Request{
		URL:    url,
		Method: operationToHTTPMethod(operation),
		Header: header,
		Body:   ioutil.NopCloser(bytes.NewReader(documentData)),
	}
	response, err := service.Do(request, time.Second*60)
	if response == nil {
		return util.Failure("Request failed: " + err.Error())
	}

	defer response.Body.Close()
	if response.StatusCode == 200 {
		return util.Success(operation + " " + documentId)
	} else if response.StatusCode/100 == 4 {
		return util.FailureWithPayload("Invalid document operation: "+response.Status, util.ReaderToJSON(response.Body))
	} else {
		return util.FailureWithPayload(service.Description()+" at "+request.URL.Host+": "+response.Status, util.ReaderToJSON(response.Body))
	}
}

func operationIn(doc map[string]interface{}) string {
	if doc["put"] != nil {
		return "put"
	} else if doc["update"] != nil {
		return "update"
	} else if doc["remove"] != nil {
		return "remove"
	} else {
		return ""
	}
}

func operationToHTTPMethod(operation string) string {
	switch operation {
	case "put":
		return "POST"
	case "update":
		return "PUT"
	case "remove":
		return "DELETE"
	}
	panic("Unexpected document operation ''" + operation + "'")
}

func Get(documentId string, service *Service) util.OperationResult {
	documentPath, documentPathError := IdToURLPath(documentId)
	if documentPathError != nil {
		return util.Failure("Invalid document id '" + documentId + "': " + documentPathError.Error())
	}

	url, urlParseError := url.Parse(service.BaseURL + "/document/v1/" + documentPath)
	if urlParseError != nil {
		return util.Failure("Invalid request path: '" + service.BaseURL + "/document/v1/" + documentPath + "': " + urlParseError.Error())
	}

	request := &http.Request{
		URL:    url,
		Method: "GET",
	}
	response, err := service.Do(request, time.Second*60)
	if response == nil {
		return util.Failure("Request failed: " + err.Error())
	}

	defer response.Body.Close()
	if response.StatusCode == 200 {
		return util.SuccessWithPayload("Read "+documentId, util.ReaderToJSON(response.Body))
	} else if response.StatusCode/100 == 4 {
		return util.FailureWithPayload("Invalid document operation: "+response.Status, util.ReaderToJSON(response.Body))
	} else {
		return util.FailureWithPayload(service.Description()+" at "+request.URL.Host+": "+response.Status, util.ReaderToJSON(response.Body))
	}
}
