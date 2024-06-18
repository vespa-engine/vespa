// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package vespa

import (
	"bytes"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/curl"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
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

var errAuth = errors.New("auth failed")

var (
	// ErrWaitTimeout is the error returned when waiting for something times out.
	ErrWaitTimeout = errors.New("wait deadline reached")
	// ErrDeployment is the error returned for terminal deployment failures.
	ErrDeployment = errors.New("deployment failed")
)

// Authenticator authenticates the given HTTP request.
type Authenticator interface {
	Authenticate(request *http.Request) error
}

// CurlWriter configures printing of Curl-equivalent commands for HTTP requests passing through a Service.
type CurlWriter struct {
	Writer    io.Writer
	InputFile string
}

func (c *CurlWriter) print(request *http.Request, tlsOptions TLSOptions, timeout time.Duration) error {
	if c.Writer == nil {
		return nil
	}
	cmd, err := curl.RawArgs(request.URL.String())
	if err != nil {
		return err
	}
	cmd.Method = request.Method
	for k, vs := range request.Header {
		for _, v := range vs {
			cmd.Header(k, v)
		}
	}
	cmd.CaCertificate = tlsOptions.CACertificateFile
	cmd.Certificate = tlsOptions.CertificateFile
	cmd.PrivateKey = tlsOptions.PrivateKeyFile
	cmd.Timeout = timeout
	if c.InputFile != "" {
		cmd.WithBodyFile(c.InputFile)
	}
	_, err = fmt.Fprintln(c.Writer, cmd.String())
	return err
}

// Service represents a Vespa service.
type Service struct {
	BaseURL    string
	Name       string
	TLSOptions TLSOptions
	CurlWriter CurlWriter

	deployAPI     bool
	auth          Authenticator
	httpClient    httputil.Client
	customClient  bool
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

	// CompatibleWith returns nil if target is compatible with the given version.
	CompatibleWith(version version.Version) error
}

// TLSOptions holds the client certificate to use for cloud API or service requests.
type TLSOptions struct {
	KeyPair  []tls.Certificate
	TrustAll bool

	CACertificatePEM []byte
	CertificatePEM   []byte
	PrivateKeyPEM    []byte

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
	if !s.customClient {
		// Do not override TLS config if a custom client has been configured
		httputil.ConfigureTLS(s.httpClient, s.TLSOptions.KeyPair, s.TLSOptions.CACertificatePEM, s.TLSOptions.TrustAll)
	}
	if s.auth != nil {
		if err := s.auth.Authenticate(request); err != nil {
			return nil, fmt.Errorf("%w: %s", errAuth, err)
		}
	}
	if err := s.CurlWriter.print(request, s.TLSOptions, timeout); err != nil {
		return nil, err
	}
	resp, err := s.httpClient.Do(request, timeout)
	if isTLSAlert(err) {
		return nil, fmt.Errorf("%w: %s", errAuth, err)
	}
	return resp, err
}

// SetClient sets a custom HTTP client that this service should use.
func (s *Service) SetClient(client httputil.Client) {
	s.httpClient = client
	s.customClient = true
}

// Wait polls the health check of this service until it succeeds or timeout passes.
func (s *Service) Wait(timeout time.Duration) error {
	// A path that does not need authentication, on any target
	url := strings.TrimRight(s.BaseURL, "/") + "/status.html"
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return err
	}
	okFunc := func(status int, response []byte) (bool, error) {
		// Always retry 404 as /status.html may return 404 while a cluster is becoming ready
		if status == 404 {
			return false, nil
		}
		return isOK(status)
	}
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

func deployServiceWait(target Target, fn responseFunc, reqFn requestFunc, timeout, retryInterval time.Duration) (int, error) {
	deployService, err := target.DeployService()
	if err != nil {
		return 0, err
	}
	return wait(deployService, fn, reqFn, timeout, retryInterval)
}

func pollLogs(target Target, logsURL string, options LogOptions, retryInterval time.Duration) error {
	req, err := http.NewRequest("GET", logsURL, nil)
	if err != nil {
		return err
	}
	lastFrom := options.From
	requestFunc := func() *http.Request {
		fromMillis := lastFrom.Unix() * 1000
		q := req.URL.Query()
		q.Set("from", strconv.FormatInt(fromMillis, 10))
		if !options.To.IsZero() {
			toMillis := options.To.Unix() * 1000
			q.Set("to", strconv.FormatInt(toMillis, 10))
		}
		req.URL.RawQuery = q.Encode()
		return req
	}
	logFunc := func(status int, response []byte) (bool, error) {
		if ok, err := isOK(status); !ok {
			return ok, err
		}
		logEntries, err := ReadLogEntries(bytes.NewReader(response))
		if err != nil {
			return false, err
		}
		for _, le := range logEntries {
			if !le.Time.After(lastFrom) {
				continue
			}
			if LogLevel(le.Level) > options.Level {
				continue
			}
			fmt.Fprintln(options.Writer, le.Format(options.Dequote))
		}
		if len(logEntries) > 0 {
			lastFrom = logEntries[len(logEntries)-1].Time
		}
		return false, nil
	}
	var timeout time.Duration
	if options.Follow {
		timeout = math.MaxInt64 // No timeout
	}
	// Ignore wait error because logFunc has no concept of completion, we just want to print log entries until timeout is reached
	if _, err := deployServiceWait(target, logFunc, requestFunc, timeout, retryInterval); err != nil && !errors.Is(err, ErrWaitTimeout) {
		return fmt.Errorf("failed to read logs: %s", err)
	}
	return nil
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
		response, err = service.Do(reqFn(), 20*time.Second)
		if errors.Is(err, errAuth) {
			return status, fmt.Errorf("aborting wait: %w", err)
		} else if err == nil {
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
		return status, ErrWaitTimeout
	}
	return status, err
}
