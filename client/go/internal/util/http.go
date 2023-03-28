// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"bytes"
	"crypto/tls"
	"fmt"
	"net/http"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/build"
	"golang.org/x/net/http2"
)

type HTTPClient interface {
	Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
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

func SetCertificate(client HTTPClient, certificates []tls.Certificate) {
	c, ok := client.(*defaultHTTPClient)
	if !ok {
		return
	}
	// Use HTTP/2 transport explicitly. Connection reuse does not work properly when using regular http.Transport, even
	// though it upgrades to HTTP/2 automatically
	// https://github.com/golang/go/issues/16582
	// https://github.com/golang/go/issues/22091
	var transport *http2.Transport
	if _, ok := c.client.Transport.(*http.Transport); ok {
		transport = &http2.Transport{}
		c.client.Transport = transport
	} else if t, ok := c.client.Transport.(*http2.Transport); ok {
		transport = t
	} else {
		panic(fmt.Sprintf("unknown transport type: %T", c.client.Transport))
	}
	if ok && !c.hasCertificates(transport.TLSClientConfig, certificates) {
		transport.TLSClientConfig = &tls.Config{
			Certificates: certificates,
			MinVersion:   tls.VersionTLS12,
		}
	}
}

func (c *defaultHTTPClient) hasCertificates(tlsConfig *tls.Config, certs []tls.Certificate) bool {
	if tlsConfig == nil {
		return false
	}
	if len(tlsConfig.Certificates) != len(certs) {
		return false
	}
	for i := 0; i < len(certs); i++ {
		if len(tlsConfig.Certificates[i].Certificate) != len(certs[i].Certificate) {
			return false
		}
		for j := 0; j < len(certs[i].Certificate); j++ {
			if !bytes.Equal(tlsConfig.Certificates[i].Certificate[j], certs[i].Certificate[j]) {
				return false
			}
		}
	}
	return true
}

func CreateClient(timeout time.Duration) HTTPClient {
	return &defaultHTTPClient{client: &http.Client{
		Timeout:   timeout,
		Transport: http.DefaultTransport,
	}}
}
