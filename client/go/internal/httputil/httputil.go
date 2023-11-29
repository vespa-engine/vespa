// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package httputil

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

// Client represents a HTTP client usable by the Vespa CLI.
type Client interface {
	Do(request *http.Request, timeout time.Duration) (response *http.Response, error error)
}

type defaultClient struct {
	client *http.Client
}

func (c *defaultClient) Do(request *http.Request, timeout time.Duration) (response *http.Response, error error) {
	if c.client.Timeout != timeout { // Set wanted timeout
		c.client.Timeout = timeout
	}
	if request.Header == nil {
		request.Header = make(http.Header)
	}
	request.Header.Set("User-Agent", fmt.Sprintf("Vespa CLI/%s", build.Version))
	return c.client.Do(request)
}

// ConfigureTLS configures the given client with given certificates and caCertificate. If trustAll is true, the client
// will skip verification of the certificate chain.
func ConfigureTLS(client Client, certificates []tls.Certificate, caCertificate []byte, trustAll bool) {
	c, ok := client.(*defaultClient)
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

// ForceHTTP2 configures the given client exclusively with a HTTP/2 transport. The other options are passed to
// ConfigureTLS. If certificates is nil, the client will be configured with H2C (HTTP/2 over clear-text).
func ForceHTTP2(client Client, certificates []tls.Certificate, caCertificate []byte, trustAll bool) {
	c, ok := client.(*defaultClient)
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
		DisableCompression: true,
		AllowHTTP:          true,
		DialTLSContext:     dialFunc,
	}
	ConfigureTLS(client, certificates, caCertificate, trustAll)
}

// NewClients creates a new HTTP client the given default timeout.
func NewClient(timeout time.Duration) Client {
	return &defaultClient{
		client: &http.Client{
			Timeout:   timeout,
			Transport: http.DefaultTransport,
		},
	}
}
