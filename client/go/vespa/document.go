// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa document API client
// Author: bratseth

package vespa

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"os"
	"time"

	"github.com/vespa-engine/vespa/client/go/curl"
	"github.com/vespa-engine/vespa/client/go/util"
)

// Sends the operation given in the file
func Send(jsonFile string, service *Service, options OperationOptions) util.OperationResult {
	return sendOperation("", jsonFile, service, anyOperation, options)
}

func Put(documentId string, jsonFile string, service *Service, options OperationOptions) util.OperationResult {
	return sendOperation(documentId, jsonFile, service, putOperation, options)
}

func Update(documentId string, jsonFile string, service *Service, options OperationOptions) util.OperationResult {
	return sendOperation(documentId, jsonFile, service, updateOperation, options)
}

func RemoveId(documentId string, service *Service, options OperationOptions) util.OperationResult {
	return sendOperation(documentId, "", service, removeOperation, options)
}

func RemoveOperation(jsonFile string, service *Service, options OperationOptions) util.OperationResult {
	return sendOperation("", jsonFile, service, removeOperation, options)
}

const (
	anyOperation    string = "any"
	putOperation    string = "put"
	updateOperation string = "update"
	removeOperation string = "remove"
)

type OperationOptions struct {
	CurlOutput io.Writer
	Timeout    time.Duration
}

func sendOperation(documentId string, jsonFile string, service *Service, operation string, options OperationOptions) util.OperationResult {
	header := http.Header{}
	header.Add("Content-Type", "application/json")

	var documentData []byte
	if operation == "remove" && jsonFile == "" {
		documentData = []byte("{\n    \"remove\": \"" + documentId + "\"\n}\n")
	} else {
		fileReader, err := os.Open(jsonFile)
		if err != nil {
			return util.FailureWithDetail("Could not open file '"+jsonFile+"'", err.Error())
		}
		defer fileReader.Close()
		documentData, err = io.ReadAll(fileReader)
		if err != nil {
			return util.FailureWithDetail("Failed to read '"+jsonFile+"'", err.Error())
		}
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
		Body:   io.NopCloser(bytes.NewReader(documentData)),
	}
	response, err := serviceDo(service, request, jsonFile, options)
	if err != nil {
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
	util.JustExitMsg("Unexpected document operation ''" + operation + "'")
	panic("unreachable")
}

func serviceDo(service *Service, request *http.Request, filename string, options OperationOptions) (*http.Response, error) {
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
	cmd.WithBodyFile(filename)
	cmd.Certificate = service.TLSOptions.CertificateFile
	cmd.PrivateKey = service.TLSOptions.PrivateKeyFile
	out := cmd.String() + "\n"
	if _, err := io.WriteString(options.CurlOutput, out); err != nil {
		return nil, err
	}
	return service.Do(request, options.Timeout)
}

func Get(documentId string, service *Service, options OperationOptions) util.OperationResult {
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
	response, err := serviceDo(service, request, "", options)
	if err != nil {
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
