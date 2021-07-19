// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Basic testing of our HTTP client wrapper
// Author: bratseth

package utils

import (
    "bytes"
    "github.com/stretchr/testify/assert"
	"io/ioutil"
    "net/http"
    "testing"
)

type mockHttpClient struct {}

func (c mockHttpClient) Get(url string) (response *http.Response, error error) {
    var status int
    var body string
    if (url == "http://host/okpath") {
        status = 200
        body = "OK"
    } else {
        status = 500
        body = "Unexpected url"
    }

    return &http.Response{
        StatusCode: status,
        Body:       ioutil.NopCloser(bytes.NewBufferString(body)),
        Header:     make(http.Header),
    },
    nil
}

func TestHttpRequest(t *testing.T) {
    ActiveHttpClient = mockHttpClient{}

    response := HttpRequest("http://host", "/okpath", "description")
    assert.Equal(t, 200, response.StatusCode)

    response = HttpRequest("http://host", "/otherpath", "description")
    assert.Equal(t, 500, response.StatusCode)
}

