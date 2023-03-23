// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"crypto/tls"
	"fmt"
	"net/http"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/build"
)

type HTTPClient interface {
	Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
	Transport() *http.Transport
}

type defaultHTTPClient struct {
	client    *http.Client
	transport *http.Transport
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

func (c *defaultHTTPClient) Transport() *http.Transport { return c.transport }

func SetCertificate(client HTTPClient, certificates []tls.Certificate) {
	client.Transport().TLSClientConfig = &tls.Config{
		Certificates: certificates,
		MinVersion:   tls.VersionTLS12,
	}
}

func CreateClient(timeout time.Duration) HTTPClient {
	transport := http.Transport{
		ForceAttemptHTTP2: true,
	}
	return &defaultHTTPClient{
		client: &http.Client{
			Timeout:   timeout,
			Transport: &transport,
		},
		transport: &transport,
	}
}
