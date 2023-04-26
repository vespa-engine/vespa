// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"net"
	"net/http"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/build"
	"golang.org/x/net/http2"
)

type HTTPClient interface {
	Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
	Clone() HTTPClient
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

func (c *defaultHTTPClient) Clone() HTTPClient { return CreateClient(c.client.Timeout) }

func ConfigureTLS(client HTTPClient, certificates []tls.Certificate, caCertificate []byte, trustAll bool) {
	c, ok := client.(*defaultHTTPClient)
	if !ok {
		return
	}
	var tlsConfig *tls.Config = nil
	if certificates != nil {
		tlsConfig = &tls.Config{
			Certificates:       certificates,
			MinVersion:         tls.VersionTLS12,
			InsecureSkipVerify: trustAll,
		}
		if caCertificate != nil {
			certs := x509.NewCertPool()
			certs.AppendCertsFromPEM(caCertificate)
			tlsConfig.RootCAs = certs
		}
	}
	if tr, ok := c.client.Transport.(*http.Transport); ok {
		tr.TLSClientConfig = tlsConfig
	} else if tr, ok := c.client.Transport.(*http2.Transport); ok {
		tr.TLSClientConfig = tlsConfig
	} else {
		panic(fmt.Sprintf("unknown transport type: %T", c.client.Transport))
	}
}

func ForceHTTP2(client HTTPClient, certificates []tls.Certificate, caCertificate []byte, trustAll bool) {
	c, ok := client.(*defaultHTTPClient)
	if !ok {
		return
	}
	var dialFunc func(ctx context.Context, network, addr string, cfg *tls.Config) (net.Conn, error)
	if certificates == nil {
		// No certificate, so force H2C (HTTP/2 over clear-text) by using a non-TLS Dialer
		dialer := net.Dialer{}
		dialFunc = func(ctx context.Context, network, addr string, cfg *tls.Config) (net.Conn, error) {
			return dialer.DialContext(ctx, network, addr)
		}
	}
	// Use HTTP/2 transport explicitly. Connection reuse does not work properly when using regular http.Transport, even
	// though it upgrades to HTTP/2 automatically
	// https://github.com/golang/go/issues/16582
	// https://github.com/golang/go/issues/22091
	c.client.Transport = &http2.Transport{
		AllowHTTP:                  true,
		DialTLSContext:             dialFunc,
		StrictMaxConcurrentStreams: true,
	}
	ConfigureTLS(client, certificates, caCertificate, trustAll)
}

func CreateClient(timeout time.Duration) HTTPClient {
	return &defaultHTTPClient{client: &http.Client{
		Timeout:   timeout,
		Transport: http.DefaultTransport,
	}}
}
