// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"crypto/tls"
	"fmt"
	"net/http"
	"time"

	"github.com/vespa-engine/vespa/client/go/build"
)

type HTTPClient interface {
	Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
	UseCertificate(certificate []tls.Certificate)
}

type defaultHTTPClient struct {
	client *http.Client
}

func (c *defaultHTTPClient) Do(request *http.Request, timeout time.Duration) (response *http.Response, error error) {
	if c.client.Timeout != timeout { // Set wanted timeout
		c.client.Timeout = timeout
	}
	if request.Header == nil {
		request.Header = make(http.Header)
	}
	request.Header.Set("User-Agent", fmt.Sprintf("Vespa CLI/%s", build.Version))
	return c.client.Do(request)
}

func (c *defaultHTTPClient) UseCertificate(certificates []tls.Certificate) {
	c.client.Transport = &http.Transport{TLSClientConfig: &tls.Config{
		Certificates: certificates,
	}}
}

func CreateClient(timeout time.Duration) HTTPClient {
	return &defaultHTTPClient{client: &http.Client{Timeout: timeout}}
}
