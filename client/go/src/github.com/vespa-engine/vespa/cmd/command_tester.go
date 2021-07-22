// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A helper for testing commands
// Author: bratseth

package cmd

import (
    "bytes"
    "github.com/vespa-engine/vespa/utils"
    "github.com/stretchr/testify/assert"
	"io/ioutil"
    "net/http"
    "testing"
)

// The HTTP status code that will be returned from the next invocation. Defauult: 200
var nextStatus int

// A recording of the last HTTP request made through this
var lastRequest *http.Request

func init() {
    nextStatus = 200
}

func executeCommand(t *testing.T, args []string) (standardout string) {
    utils.ActiveHttpClient = mockHttpClient{}
	b := bytes.NewBufferString("")
    utils.Out = b
	rootCmd.SetArgs(args)
	rootCmd.Execute()
	out, err := ioutil.ReadAll(b)
	assert.Empty(t, err, "No error")
	return string(out)
}

type mockHttpClient struct {
}

func (c mockHttpClient) Do(request *http.Request) (response *http.Response, error error) {
    lastRequest = request
    return &http.Response{
        StatusCode: nextStatus,
        Body:       ioutil.NopCloser(bytes.NewBufferString("")),
        Header:     make(http.Header),
    },
    nil
}
