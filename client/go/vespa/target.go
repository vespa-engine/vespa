package vespa

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/vespa-engine/vespa/util"
)

const (
	localTargetType  = "local"
	customTargetType = "custom"
	cloudTargetType  = "cloud"

	deployService   = "deploy"
	queryService    = "query"
	documentService = "document"

	defaultCloudAPI = "https://api.vespa-external.aws.oath.cloud:4443"

	waitRetryInterval = 2 * time.Second
)

// Service represents a Vespa service.
type Service struct {
	BaseURL     string
	Name        string
	description string
	certificate tls.Certificate
}

// Target represents a Vespa platform, running named Vespa services.
type Target interface {
	// Type returns this target's type, e.g. local or cloud.
	Type() string

	// Service returns the service for given name.
	Service(name string) (*Service, error)

	// DiscoverServices queries for services available on this target until one is found or timeout passes.
	DiscoverServices(timeout time.Duration) error
}

type customTarget struct {
	targetType string
	baseURL    string
}

type localTarget struct{ targetType string }

// Do sends request to this service. Any required authentication happens automatically.
func (s *Service) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	if s.certificate.Certificate != nil {
		util.ActiveHttpClient.UseCertificate(s.certificate)
	}
	return util.HttpDo(request, timeout, s.Description())
}

// Wait polls the health check of this service until it succeeds or timeout passes.
func (s *Service) Wait(timeout time.Duration) (int, error) {
	url := s.BaseURL
	switch s.Name {
	case deployService:
		url += "/status.html" // because /ApplicationStatus is not publicly reachable in Vespa Cloud
	case queryService, documentService:
		url += "/ApplicationStatus"
	default:
		return 0, fmt.Errorf("invalid service: %s", s.Name)
	}
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return 0, err
	}
	okFunc := func(status int, response []byte) (string, bool) { return "", status/100 == 2 }
	status, _, err := wait(okFunc, req, s.certificate, timeout)
	return status, err
}

func (s *Service) Description() string {
	switch s.Name {
	case queryService:
		return "Container (query API)"
	case documentService:
		return "Container (document API)"
	case deployService:
		return "Deploy API"
	}
	return fmt.Sprintf("No description of service %s", s.Name)
}

func (t *customTarget) Type() string { return t.targetType }

func (t *customTarget) Service(name string) (*Service, error) {
	switch name {
	case deployService, queryService, documentService:
		// TODO: Add default ports if missing
		return &Service{BaseURL: t.baseURL, Name: name}, nil
	}
	return nil, fmt.Errorf("unknown service: %s", name)
}

func (t *customTarget) DiscoverServices(timeout time.Duration) error { return nil }

func (t *localTarget) Type() string { return t.targetType }

func (t *localTarget) Service(name string) (*Service, error) {
	switch name {
	case deployService:
		return &Service{Name: name, BaseURL: "http://127.0.0.1:19071"}, nil
	case queryService, documentService:
		return &Service{Name: name, BaseURL: "http://127.0.0.1:8080"}, nil
	}
	return nil, fmt.Errorf("unknown service: %s", name)
}

func (t *localTarget) DiscoverServices(timeout time.Duration) error { return nil }

type cloudTarget struct {
	cloudAPI   string
	targetType string
	deployment Deployment
	keyPair    tls.Certificate
	apiKey     []byte

	queryURL    string
	documentURL string
}

func (t *cloudTarget) Type() string { return t.targetType }

func (t *cloudTarget) Service(name string) (*Service, error) {
	switch name {
	case deployService:
		return &Service{Name: name, BaseURL: t.cloudAPI}, nil
	case queryService:
		if t.queryURL == "" {
			return nil, fmt.Errorf("service %s not discovered", name)
		}
		return &Service{Name: name, BaseURL: t.queryURL, certificate: t.keyPair}, nil
	case documentService:
		if t.documentURL == "" {
			return nil, fmt.Errorf("service %s not discovered", name)
		}
		return &Service{Name: name, BaseURL: t.documentURL, certificate: t.keyPair}, nil
	}
	return nil, fmt.Errorf("unknown service: %s", name)
}

// DiscoverServices queries Vespa Cloud for endpoints until at least one endpoint is returned, or timeout passes.
func (t *cloudTarget) DiscoverServices(timeout time.Duration) error {
	deploymentURL := fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s",
		t.cloudAPI,
		t.deployment.Application.Tenant, t.deployment.Application.Application, t.deployment.Application.Instance,
		t.deployment.Zone.Environment, t.deployment.Zone.Region)
	req, err := http.NewRequest("GET", deploymentURL, nil)
	signer := NewRequestSigner(t.deployment.Application.SerializedForm(), t.apiKey)
	if err := signer.SignRequest(req); err != nil {
		return err
	}
	endpointFunc := func(status int, response []byte) (string, bool) {
		if status/100 != 2 {
			return "", false
		}
		var resp deploymentResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return "", false
		}
		if len(resp.Endpoints) == 0 {
			return "", false
		}
		return resp.Endpoints[0].URL, true
	}
	_, endpoint, err := wait(endpointFunc, req, t.keyPair, timeout)
	if err != nil {
		return err
	}
	if endpoint == "" {
		return fmt.Errorf("no endpoint discovered")
	}
	t.queryURL = endpoint
	t.documentURL = endpoint
	return nil
}

// LocalTarget creates a target for a Vespa platform running locally.
func LocalTarget() Target { return &localTarget{targetType: localTargetType} }

// CustomTarget creates a Target for a Vespa platform running at baseURL.
func CustomTarget(baseURL string) Target {
	return &customTarget{targetType: customTargetType, baseURL: baseURL}
}

// CloudTarget creates a Target for the Vespa Cloud platform.
func CloudTarget(deployment Deployment, keyPair tls.Certificate, apiKey []byte) Target {
	return &cloudTarget{
		cloudAPI:   defaultCloudAPI,
		targetType: cloudTargetType,
		deployment: deployment,
		keyPair:    keyPair,
		apiKey:     apiKey,
	}
}

type deploymentEndpoint struct {
	URL string `json:"url"`
}

type deploymentResponse struct {
	Endpoints []deploymentEndpoint `json:"endpoints"`
}

type responseFunc func(status int, response []byte) (string, bool)

func wait(fn responseFunc, req *http.Request, certificate tls.Certificate, timeout time.Duration) (int, string, error) {
	if certificate.Certificate != nil {
		util.ActiveHttpClient.UseCertificate(certificate)
	}
	var (
		httpErr    error
		response   *http.Response
		statusCode int
	)
	deadline := time.Now().Add(timeout)
	loopOnce := timeout == 0
	for time.Now().Before(deadline) || loopOnce {
		response, httpErr = util.HttpDo(req, 10*time.Second, "")
		if httpErr == nil {
			statusCode = response.StatusCode
			body, err := io.ReadAll(response.Body)
			if err != nil {
				return 0, "", err
			}
			response.Body.Close()
			result, ok := fn(statusCode, body)
			if ok {
				return statusCode, result, nil
			}
		}
		if loopOnce {
			break
		}
		time.Sleep(waitRetryInterval)
	}
	return statusCode, "", httpErr
}
