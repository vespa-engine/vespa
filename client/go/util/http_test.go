// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Basic testing of our HTTP client wrapper
// Author: bratseth

package util

import (
	"bytes"
	"crypto/tls"
	"io/ioutil"
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

type mockHttpClient struct{}

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

func (c mockHttpClient) UseCertificate(certificate tls.Certificate) {}

func TestHttpRequest(t *testing.T) {
	ActiveHttpClient = mockHttpClient{}

	response, err := HttpGet("http://host", "/okpath", "description")
	assert.Nil(t, err)
	assert.Equal(t, 200, response.StatusCode)

	response, err = HttpGet("http://host", "/otherpath", "description")
	assert.Nil(t, err)
	assert.Equal(t, 500, response.StatusCode)
}
