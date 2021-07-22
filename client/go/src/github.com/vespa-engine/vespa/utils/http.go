// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A HTTP wrapper which handles some errors and provides a way to replace the HTTP client by a mock.
// Author: bratseth

package utils

import (
    "bufio"
    "net/http"
    "net/url"
    "strings"
    "time"
)

// Set this to a mock HttpClient to unit test HTTP requests
var ActiveHttpClient = CreateClient()

type HttpClient interface {
    Do(*http.Request) (response *http.Response, error error)
}

type defaultHttpClient struct {
    client http.Client
}

func (c defaultHttpClient) Do(request *http.Request) (response *http.Response, error error) {
    return c.client.Do(request)
}

func CreateClient() (client HttpClient) {
    return &defaultHttpClient{
        client: http.Client{Timeout: time.Second * 10,},
    }
}

// Convenience function for doing a HTTP GET
func HttpGet(host string, path string, description string) (response *http.Response) {
    url, urlError := url.Parse(host + path)
    if urlError != nil {
        Error("Invalid target url '" + host + path + "'")
        return nil
    }
    return HttpDo(&http.Request{URL: url,}, description)
}

func HttpDo(request *http.Request, description string) (response *http.Response) {
    response, error := ActiveHttpClient.Do(request)
    if error != nil {
        Error("Could not connect to", strings.ToLower(description), "at", request.URL.Host)
        Detail(error.Error())
        return
    }
    defer response.Body.Close()

    scanner := bufio.NewScanner(response.Body)

    if error := scanner.Err(); error != nil {
        Error("Error reading data from", strings.ToLower(description), "at", request.URL.Host)
        Detail(error.Error())
        return nil
    } else {
        return response
    }
}
