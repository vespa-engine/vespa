// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"crypto/tls"
	"net/http"
	"time"
)

type HTTPClient interface {
	Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
	UseCertificate(certificate []tls.Certificate)
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

func (c *defaultHttpClient) UseCertificate(certificates []tls.Certificate) {
	c.client.Transport = &http.Transport{TLSClientConfig: &tls.Config{
		Certificates: certificates,
	}}
}

func CreateClient(timeout time.Duration) HTTPClient {
	return &defaultHttpClient{client: &http.Client{Timeout: timeout}}
}
