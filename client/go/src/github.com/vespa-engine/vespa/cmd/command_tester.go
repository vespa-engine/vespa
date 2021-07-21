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

var expectedUrl string

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

type mockHttpClient struct {}

func (c mockHttpClient) Do(request *http.Request) (response *http.Response, error error) {
    var status int
    var body string
    if request.URL.String() == expectedUrl {
        status = 200
        body = "OK"
    } else {
        status = 400
        body = "Unexpected url " + request.URL.String()
    }

    return &http.Response{
        StatusCode: status,
        Body:       ioutil.NopCloser(bytes.NewBufferString(body)),
        Header:     make(http.Header),
    },
    nil
}
