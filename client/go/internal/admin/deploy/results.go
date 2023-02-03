// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"encoding/json"
	"strings"
)

func decodeResponse(response string, v interface{}) (code int, err error) {
	codec := json.NewDecoder(strings.NewReader(response))
	err = codec.Decode(v)
	if err != nil {
		return
	}
	err = codec.Decode(&code)
	return
}

type UploadResult struct {
	Log []struct {
		Time               int64  `json:"time"`
		Level              string `json:"level"`
		Message            string `json:"message"`
		ApplicationPackage bool   `json:"applicationPackage"`
	} `json:"log"`
	Tenant    string `json:"tenant"`
	SessionID string `json:"session-id"`
	Prepared  string `json:"prepared"`
	Content   string `json:"content"`
	Message   string `json:"message"`
	ErrorCode string `json:"error-code"`
}

type PrepareResult struct {
	Log []struct {
		Time               int64  `json:"time"`
		Level              string `json:"level"`
		Message            string `json:"message"`
		ApplicationPackage bool   `json:"applicationPackage"`
	} `json:"log"`
	Tenant    string `json:"tenant"`
	SessionID string `json:"session-id"`
	Activate  string `json:"activate"`
	Message   string `json:"message"`
	ErrorCode string `json:"error-code"`
	/* not used at the moment:
	ConfigChangeActions struct {
		Restart []struct {
			ClusterName string   `json:"clusterName"`
			ClusterType string   `json:"clusterType"`
			ServiceType string   `json:"serviceType"`
			Messages    []string `json:"messages"`
			Services    []struct {
				ServiceName string `json:"serviceName"`
				ServiceType string `json:"serviceType"`
				ConfigID    string `json:"configId"`
				HostName    string `json:"hostName"`
			} `json:"services"`
		} `json:"restart"`
		 Refeed  []interface{} `json:"refeed"`
		Reindex []interface{} `json:"reindex"`
	} `json:"configChangeActions"`
	*/
}

type ActivateResult struct {
	Deploy struct {
		From             string `json:"from"`
		Timestamp        int64  `json:"timestamp"`
		InternalRedeploy bool   `json:"internalRedeploy"`
	} `json:"deploy"`
	Application struct {
		ID                       string `json:"id"`
		Checksum                 string `json:"checksum"`
		Generation               int    `json:"generation"`
		PreviousActiveGeneration int    `json:"previousActiveGeneration"`
	} `json:"application"`
	Tenant    string `json:"tenant"`
	SessionID string `json:"session-id"`
	Message   string `json:"message"`
	URL       string `json:"url"`
	ErrorCode string `json:"error-code"`
}
