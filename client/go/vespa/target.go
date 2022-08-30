// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package vespa

import (
	"crypto/tls"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/version"
)

const (
	// A target for a local Vespa service
	TargetLocal = "local"

	// A target for a custom URL
	TargetCustom = "custom"

	// A Vespa Cloud target
	TargetCloud = "cloud"

	// A hosted Vespa target
	TargetHosted = "hosted"

	// A Vespa service that handles deployments, either a config server or a controller
	DeployService = "deploy"

	// A Vespa service that handles queries.
	QueryService = "query"

	// A Vespa service that handles feeding of document. This may point to the same service as QueryService.
	DocumentService = "document"

	retryInterval = 2 * time.Second
)

// Service represents a Vespa service.
type Service struct {
	BaseURL    string
	Name       string
	TLSOptions TLSOptions
	ztsClient  ztsClient
	httpClient util.HTTPClient
}

// Target represents a Vespa platform, running named Vespa services.
type Target interface {
	// Type returns this target's type, e.g. local or cloud.
	Type() string

	// IsCloud returns whether this target is Vespa Cloud or hosted Vespa
	IsCloud() bool

	// Deployment returns the deployment managed by this target.
	Deployment() Deployment

	// Service returns the service for given name. If timeout is non-zero, wait for the service to converge.
	Service(name string, timeout time.Duration, sessionOrRunID int64, cluster string) (*Service, error)

	// PrintLog writes the logs of this deployment using given options to control output.
	PrintLog(options LogOptions) error

	// SignRequest signs request with given keyID as required by the implementation of this target.
	SignRequest(request *http.Request, keyID string) error

	// CheckVersion verifies whether clientVersion is compatible with this target.
	CheckVersion(clientVersion version.Version) error
}

// TLSOptions configures the client certificate to use for cloud API or service requests.
type TLSOptions struct {
	KeyPair         tls.Certificate
	CertificateFile string
	PrivateKeyFile  string
	AthenzDomain    string
}

// LogOptions configures the log output to produce when writing log messages.
type LogOptions struct {
	From    time.Time
	To      time.Time
	Follow  bool
	Dequote bool
	Writer  io.Writer
	Level   int
}

// Do sends request to this service. Any required authentication happens automatically.
func (s *Service) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	if s.TLSOptions.KeyPair.Certificate != nil {
		s.httpClient.UseCertificate([]tls.Certificate{s.TLSOptions.KeyPair})
	}
	if s.TLSOptions.AthenzDomain != "" {
		accessToken, err := s.ztsClient.AccessToken(s.TLSOptions.AthenzDomain, s.TLSOptions.KeyPair)
		if err != nil {
			return nil, err
		}
		if request.Header == nil {
			request.Header = make(http.Header)
		}
		request.Header.Add("Authorization", "Bearer "+accessToken)
	}
	return s.httpClient.Do(request, timeout)
}

// Wait polls the health check of this service until it succeeds or timeout passes.
func (s *Service) Wait(timeout time.Duration) (int, error) {
	url := s.BaseURL
	switch s.Name {
	case DeployService:
		url += "/status.html" // because /ApplicationStatus is not publicly reachable in Vespa Cloud
	case QueryService, DocumentService:
		url += "/ApplicationStatus"
	default:
		return 0, fmt.Errorf("invalid service: %s", s.Name)
	}
	return waitForOK(s.httpClient, url, &s.TLSOptions.KeyPair, timeout)
}

func (s *Service) Description() string {
	switch s.Name {
	case QueryService:
		return "Container (query API)"
	case DocumentService:
		return "Container (document API)"
	case DeployService:
		return "Deploy API"
	}
	return fmt.Sprintf("No description of service %s", s.Name)
}

func isOK(status int) bool { return status/100 == 2 }

type responseFunc func(status int, response []byte) (bool, error)

type requestFunc func() *http.Request

// waitForOK queries url and returns its status code. If the url returns a non-200 status code, it is repeatedly queried
// until timeout elapses.
func waitForOK(client util.HTTPClient, url string, certificate *tls.Certificate, timeout time.Duration) (int, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return 0, err
	}
	okFunc := func(status int, response []byte) (bool, error) { return isOK(status), nil }
	return wait(client, okFunc, func() *http.Request { return req }, certificate, timeout)
}

func wait(client util.HTTPClient, fn responseFunc, reqFn requestFunc, certificate *tls.Certificate, timeout time.Duration) (int, error) {
	if certificate != nil {
		client.UseCertificate([]tls.Certificate{*certificate})
	}
	var (
		httpErr    error
		response   *http.Response
		statusCode int
	)
	deadline := time.Now().Add(timeout)
	loopOnce := timeout == 0
	for time.Now().Before(deadline) || loopOnce {
		req := reqFn()
		response, httpErr = client.Do(req, 10*time.Second)
		if httpErr == nil {
			statusCode = response.StatusCode
			body, err := io.ReadAll(response.Body)
			if err != nil {
				return 0, err
			}
			response.Body.Close()
			ok, err := fn(statusCode, body)
			if err != nil {
				return statusCode, err
			}
			if ok {
				return statusCode, nil
			}
		}
		timeLeft := time.Until(deadline)
		if loopOnce || timeLeft < retryInterval {
			break
		}
		time.Sleep(retryInterval)
	}
	return statusCode, httpErr
}
