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
    "time"
)

type mockHttpClient struct {}

func (c mockHttpClient) Do(request *http.Request, timeout time.Duration) (response *http.Response, error error) {
    var status int
    var body string
    if request.URL.String() == "http://host/okpath" {
        status = 200
        body = "OK body"
    } else {
        status = 500
        body = "Unexpected url body"
    }

    return &http.Response{
        StatusCode: status,
        Header:     make(http.Header),
        Body:       ioutil.NopCloser(bytes.NewBufferString(body)),
    },
    nil
}

func TestHttpRequest(t *testing.T) {
    ActiveHttpClient = mockHttpClient{}

    response := HttpGet("http://host", "/okpath", "description")
    assert.Equal(t, 200, response.StatusCode)

    response = HttpGet("http://host", "/otherpath", "description")
    assert.Equal(t, 500, response.StatusCode)
}

