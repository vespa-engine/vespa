// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A HTTP wrapper which handles some errors and provides a way to replace the HTTP client by a mock.
// Author: bratseth

package util

import (
	"crypto/tls"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/vespa-engine/vespa/client/go/build"
)

// Set this to a mock HttpClient instead to unit test HTTP requests
var ActiveHttpClient = CreateClient(time.Second * 10)

type HttpClient interface {
	Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
	UseCertificate(certificate tls.Certificate)
}

type defaultHttpClient struct {
	client *http.Client
}

func (c *defaultHttpClient) Do(request *http.Request, timeout time.Duration) (response *http.Response, error error) {
	if c.client.Timeout != timeout { // Set wanted timeout
		c.client.Timeout = timeout
	}
	return c.client.Do(request)
}

func (c *defaultHttpClient) UseCertificate(certificate tls.Certificate) {
	c.client.Transport = &http.Transport{TLSClientConfig: &tls.Config{
		Certificates: []tls.Certificate{certificate},
	}}
}

func CreateClient(timeout time.Duration) HttpClient {
	return &defaultHttpClient{
		client: &http.Client{Timeout: timeout},
	}
}

// Convenience function for doing a HTTP GET
func HttpGet(host string, path string, description string) (*http.Response, error) {
	url, err := url.Parse(host + path)
	if err != nil {
		return nil, fmt.Errorf("Invalid target URL: %s: %w", host+path, err)
	}
	return HttpDo(&http.Request{URL: url}, time.Second*10, description)
}

func HttpDo(request *http.Request, timeout time.Duration, description string) (*http.Response, error) {
	if request.Header == nil {
		request.Header = make(http.Header)
	}
	request.Header.Set("User-Agent", fmt.Sprintf("Vespa CLI/%s", build.Version))
	response, err := ActiveHttpClient.Do(request, timeout)
	if err != nil {
		return nil, err
	}
	return response, nil
}
