// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// status command tests
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

func TestStatusConfigServerCommand(t *testing.T) {
    utils.ActiveHttpClient = mockHttpClient{}
	b := bytes.NewBufferString("")
    utils.Out = b
	rootCmd.SetArgs([]string{"status", "config-server"})
	rootCmd.Execute()
	out, err := ioutil.ReadAll(b)
	assert.Empty(t, err, "No error")
	assert.Equal(t, "\x1b[32mConfig server at http://127.0.0.1:19071 is ready \n", string(out), "Get success message")
}

type mockHttpClient struct {}

func (c mockHttpClient) Get(url string) (response *http.Response, error error) {
    var status int
    var body string
    if (url == "http://127.0.0.1:19071/ApplicationStatus" || url == "http://127.0.0.1:8080/ApplicationStatus") {
        status = 200
        body = "OK"
    } else {
        status = 400
        body = "Unexpected url " + url
    }

    return &http.Response{
        StatusCode: status,
        Body:       ioutil.NopCloser(bytes.NewBufferString(body)),
        Header:     make(http.Header),
    },
    nil
}
