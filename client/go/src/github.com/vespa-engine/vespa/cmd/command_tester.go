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

// The HTTP status code that will be returned from the next invocation. Default: 200
var nextStatus int

// The response body code that will be returned from the next invocation. Default: ""
var nextBody string

// A recording of the last HTTP request made through this
var lastRequest *http.Request

func reset() {
    // Persistent flags in Cobra persists over tests
	rootCmd.SetArgs([]string{"status", "-t", ""})
	rootCmd.Execute()

    lastRequest = nil
    nextStatus = 200
    nextBody = ""
}

func executeCommand(t *testing.T, args []string, moreArgs []string) (standardout string) {
    utils.ActiveHttpClient = mockHttpClient{}
	b := bytes.NewBufferString("")
    utils.Out = b
	rootCmd.SetArgs(concat(args, moreArgs))
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
        Body:       ioutil.NopCloser(bytes.NewBufferString(nextBody)),
        Header:     make(http.Header),
    },
    nil
}

func concat(array1 []string, array2 []string) []string {
    result := make([]string, len(array1) + len(array2))
    for i := 0; i < len(array1); i++ {
        result[i] = array1[i]
    }
    for i := 0; i < len(array2); i++ {
        result[i + len(array1)] = array2[i]
    }
    return result
}

