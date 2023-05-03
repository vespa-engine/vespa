// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package vespa

import (
	"crypto/tls"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

const (
	// A target for a local Vespa service
	TargetLocal = "local"

	// A target for a Vespa service at a custom URL
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

// Authenticator authenticates the given HTTP request.
type Authenticator interface {
	Authenticate(request *http.Request) error
}

// Service represents a Vespa service.
type Service struct {
	BaseURL    string
	Name       string
	TLSOptions TLSOptions

	once       sync.Once
	auth       Authenticator
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

	// CheckVersion verifies whether clientVersion is compatible with this target.
	CheckVersion(clientVersion version.Version) error
}

// TLSOptions holds the client certificate to use for cloud API or service requests.
type TLSOptions struct {
	CACertificate []byte
	KeyPair       []tls.Certificate
	TrustAll      bool

	CACertificateFile string
	CertificateFile   string
	PrivateKeyFile    string
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

// Do sends request to this service. Authentication of the request happens automatically.
func (s *Service) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	s.once.Do(func() {
		util.ConfigureTLS(s.httpClient, s.TLSOptions.KeyPair, s.TLSOptions.CACertificate, s.TLSOptions.TrustAll)
	})
	if s.auth != nil {
		if err := s.auth.Authenticate(request); err != nil {
			return nil, err
		}
	}
	return s.httpClient.Do(request, timeout)
}

// SetClient sets the HTTP client that this service should use.
func (s *Service) SetClient(client util.HTTPClient) { s.httpClient = client }

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
	return waitForOK(s, url, timeout)
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

func isOK(status int) (bool, error) {
	class := status / 100
	switch class {
	case 2: // success
		return true, nil
	case 4: // client error
		return false, fmt.Errorf("request failed with status %d", status)
	default: // retry
		return false, nil
	}
}

type responseFunc func(status int, response []byte) (bool, error)

type requestFunc func() *http.Request

// waitForOK queries url and returns its status code. If response status is not 2xx or 4xx, it is repeatedly queried
// until timeout elapses.
func waitForOK(service *Service, url string, timeout time.Duration) (int, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return 0, err
	}
	okFunc := func(status int, response []byte) (bool, error) {
		ok, err := isOK(status)
		if err != nil {
			return false, fmt.Errorf("failed to query %s at %s: %w", service.Description(), url, err)
		}
		return ok, err
	}
	return wait(service, okFunc, func() *http.Request { return req }, timeout)
}

func wait(service *Service, fn responseFunc, reqFn requestFunc, timeout time.Duration) (int, error) {
	var (
		httpErr    error
		response   *http.Response
		statusCode int
	)
	deadline := time.Now().Add(timeout)
	loopOnce := timeout == 0
	for time.Now().Before(deadline) || loopOnce {
		req := reqFn()
		response, httpErr = service.Do(req, 10*time.Second)
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
