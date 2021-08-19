// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A HTTP wrapper which handles some errors and provides a way to replace the HTTP client by a mock.
// Author: bratseth

package utils

import (
    "net/http"
    "net/url"
    "strings"
    "time"
)

// Set this to a mock HttpClient instead to unit test HTTP requests
var ActiveHttpClient = CreateClient(time.Second * 10)

type HttpClient interface {
    Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
}

type defaultHttpClient struct {
    client *http.Client
}

func (c *defaultHttpClient) Do(request *http.Request, timeout time.Duration) (response *http.Response, error error) {
    if c.client.Timeout != timeout { // Create a new client with the right timeout
        c.client = &http.Client{Timeout: timeout,}
    }
    return c.client.Do(request)
}

func CreateClient(timeout time.Duration) HttpClient {
    return &defaultHttpClient{
        client: &http.Client{Timeout: timeout,},
    }
}

// Convenience function for doing a HTTP GET
func HttpGet(host string, path string, description string) *http.Response {
    url, urlError := url.Parse(host + path)
    if urlError != nil {
        Error("Invalid target url '" + host + path + "'")
        return nil
    }
    return HttpDo(&http.Request{URL: url,}, time.Second * 10, description)
}

func HttpDo(request *http.Request, timeout time.Duration, description string) *http.Response {
    response, error := ActiveHttpClient.Do(request, timeout)
    if error != nil {
        Error("Could not connect to", strings.ToLower(description), "at", request.URL.Host)
        Detail(error.Error())
    }
    return response
}

