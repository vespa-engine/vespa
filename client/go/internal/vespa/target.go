// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package vespa

import (
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
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

	// LatestDeployment waits for a deployment to converge to latest generation
	LatestDeployment int64 = -1

	// AnyDeployment waits for a deployment to converge on any generation
	AnyDeployment int64 = -2
)

var errWaitTimeout = errors.New("wait timed out")

// Authenticator authenticates the given HTTP request.
type Authenticator interface {
	Authenticate(request *http.Request) error
}

// Service represents a Vespa service.
type Service struct {
	BaseURL    string
	Name       string
	TLSOptions TLSOptions

	deployAPI     bool
	once          sync.Once
	auth          Authenticator
	httpClient    util.HTTPClient
	retryInterval time.Duration
}

// Target represents a Vespa platform, running named Vespa services.
type Target interface {
	// Type returns this target's type, e.g. local or cloud.
	Type() string

	// IsCloud returns whether this target is Vespa Cloud or hosted Vespa
	IsCloud() bool

	// Deployment returns the deployment managed by this target.
	Deployment() Deployment

	// DeployService returns the service providing the deploy API on this target.
	DeployService() (*Service, error)

	// ContainerServices returns all container services of the current deployment. If timeout is positive, wait for
	// services to be discovered.
	ContainerServices(timeout time.Duration) ([]*Service, error)

	// AwaitDeployment waits for a deployment identified by id to succeed. It returns the id that succeeded, or an
	// error. The exact meaning of id depends on the implementation.
	AwaitDeployment(id int64, timeout time.Duration) (int64, error)

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
func (s *Service) Wait(timeout time.Duration) error {
	url := s.BaseURL
	if s.deployAPI {
		url += "/status.html" // because /ApplicationStatus is not publicly reachable in Vespa Cloud
	} else {
		url += "/ApplicationStatus"
	}
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return err
	}
	okFunc := func(status int, response []byte) (bool, error) { return isOK(status) }
	status, err := wait(s, okFunc, func() *http.Request { return req }, timeout, s.retryInterval)
	if err != nil {
		statusDesc := ""
		if status > 0 {
			statusDesc = fmt.Sprintf(": status %d", status)
		}
		return fmt.Errorf("unhealthy %s%s%s at %s: %w", s.Description(), waitDescription(timeout), statusDesc, url, err)
	}
	return nil
}

func (s *Service) Description() string {
	if s.deployAPI {
		return "deploy API"
	}
	if s.Name == "" {
		return "container"
	}
	return "container " + s.Name
}

// FindService returns the service of given name, found among services, if any.
func FindService(name string, services []*Service) (*Service, error) {
	if name == "" && len(services) == 1 {
		return services[0], nil
	}
	names := make([]string, len(services))
	for i, s := range services {
		if name == s.Name {
			return s, nil
		}
		names[i] = s.Name
	}
	found := "no services found"
	if len(names) > 0 {
		found = "known services: " + strings.Join(names, ", ")
	}
	if name != "" {
		return nil, fmt.Errorf("no such service: %q: %s", name, found)
	}
	return nil, fmt.Errorf("no service specified: %s", found)
}

func waitDescription(d time.Duration) string {
	if d > 0 {
		return " after waiting up to " + d.String()
	}
	return ""
}

func isOK(status int) (bool, error) {
	class := status / 100
	switch class {
	case 2: // success
		return true, nil
	case 4: // client error
		return false, fmt.Errorf("got status %d", status)
	default: // retry on everything else
		return false, nil
	}
}

// responseFunc returns whether a HTTP request is considered successful, based on its status and response data.
// Returning false indicates that the operation should be retried. An error is returned if the response is considered
// terminal and that the request should not be retried.
type responseFunc func(status int, response []byte) (ok bool, err error)

type requestFunc func() *http.Request

// wait queries service until one of the following conditions are satisfied:
//
// 1. okFn returns true or a non-nil error
// 2. timeout is exceeded
//
// It returns the last received HTTP status code and error, if any.
func wait(service *Service, okFn responseFunc, reqFn requestFunc, timeout, retryInterval time.Duration) (int, error) {
	var (
		status   int
		response *http.Response
		err      error
	)
	deadline := time.Now().Add(timeout)
	loopOnce := timeout == 0
	for time.Now().Before(deadline) || loopOnce {
		response, err = service.Do(reqFn(), 10*time.Second)
		if err == nil {
			status = response.StatusCode
			body, err := io.ReadAll(response.Body)
			if err != nil {
				return 0, err
			}
			response.Body.Close()
			ok, err := okFn(status, body)
			if err != nil {
				return status, fmt.Errorf("aborting wait: %w", err)
			}
			if ok {
				return status, nil
			}
		}
		timeLeft := time.Until(deadline)
		if loopOnce || timeLeft < retryInterval {
			break
		}
		time.Sleep(retryInterval)
	}
	if err == nil {
		return status, errWaitTimeout
	}
	return status, err
}
