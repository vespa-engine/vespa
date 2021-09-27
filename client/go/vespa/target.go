package vespa

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"time"

	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	localTargetType  = "local"
	customTargetType = "custom"
	cloudTargetType  = "cloud"

	deployService   = "deploy"
	queryService    = "query"
	documentService = "document"

	waitRetryInterval = 2 * time.Second
)

// Service represents a Vespa service.
type Service struct {
	BaseURL    string
	Name       string
	TLSOptions TLSOptions
}

// Target represents a Vespa platform, running named Vespa services.
type Target interface {
	// Type returns this target's type, e.g. local or cloud.
	Type() string

	// Service returns the service for given name.
	Service(name string) (*Service, error)

	// DiscoverServices queries for services available on this target after the deployment run has completed.
	DiscoverServices(timeout time.Duration, runID int64) error
}

// TLSOptions configures the certificate to use for service requests.
type TLSOptions struct {
	KeyPair         tls.Certificate
	CertificateFile string
	PrivateKeyFile  string
}

// LogOptions configures the log output to produce when waiting for services.
type LogOptions struct {
	Writer io.Writer
	Level  int
}

type customTarget struct {
	targetType string
	baseURL    string
}

// Do sends request to this service. Any required authentication happens automatically.
func (s *Service) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	if s.TLSOptions.KeyPair.Certificate != nil {
		util.ActiveHttpClient.UseCertificate(s.TLSOptions.KeyPair)
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
	okFunc := func(status int, response []byte) (bool, error) { return status/100 == 2, nil }
	return wait(okFunc, func() *http.Request { return req }, &s.TLSOptions.KeyPair, timeout)
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
		url, err := t.urlWithPort(name)
		if err != nil {
			return nil, err
		}
		return &Service{BaseURL: url, Name: name}, nil
	}
	return nil, fmt.Errorf("unknown service: %s", name)
}

func (t *customTarget) urlWithPort(serviceName string) (string, error) {
	u, err := url.Parse(t.baseURL)
	if err != nil {
		return "", err
	}
	port := u.Port()
	if port == "" {
		switch serviceName {
		case deployService:
			port = "19071"
		case queryService, documentService:
			port = "8080"
		default:
			return "", fmt.Errorf("unknown service: %s", serviceName)
		}
		u.Host = u.Host + ":" + port
	}
	return u.String(), nil
}

func (t *customTarget) DiscoverServices(timeout time.Duration, runID int64) error {
	deployService, err := t.Service("deploy")
	if err != nil {
		return err
	}
	url := fmt.Sprintf("%s/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge", deployService.BaseURL)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return err
	}
	converged := false
	convergedFunc := func(status int, response []byte) (bool, error) {
		if status/100 != 2 {
			return false, nil
		}
		var resp serviceConvergeResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return false, nil
		}
		converged = resp.Converged
		return converged, nil
	}
	if _, err := wait(convergedFunc, func() *http.Request { return req }, nil, timeout); err != nil {
		return err
	}
	if !converged {
		return fmt.Errorf("services have not converged")
	}
	return nil
}

type cloudTarget struct {
	apiURL     string
	targetType string
	deployment Deployment
	apiKey     []byte
	tlsOptions TLSOptions
	logOptions LogOptions

	queryURL    string
	documentURL string
}

func (t *cloudTarget) Type() string { return t.targetType }

func (t *cloudTarget) Service(name string) (*Service, error) {
	switch name {
	case deployService:
		return &Service{Name: name, BaseURL: t.apiURL}, nil
	case queryService:
		if t.queryURL == "" {
			return nil, fmt.Errorf("service %s not discovered", name)
		}
		return &Service{Name: name, BaseURL: t.queryURL, TLSOptions: t.tlsOptions}, nil
	case documentService:
		if t.documentURL == "" {
			return nil, fmt.Errorf("service %s not discovered", name)
		}
		return &Service{Name: name, BaseURL: t.documentURL, TLSOptions: t.tlsOptions}, nil
	}
	return nil, fmt.Errorf("unknown service: %s", name)
}

// DiscoverServices waits for run identified by runID to complete and at least one endpoint is available, or timeout
// passes.
func (t *cloudTarget) DiscoverServices(timeout time.Duration, runID int64) error {
	signer := NewRequestSigner(t.deployment.Application.SerializedForm(), t.apiKey)
	if runID > 0 {
		if err := t.waitForRun(signer, runID, timeout); err != nil {
			return err
		}
	}
	return t.discoverEndpoints(signer, timeout)
}

func (t *cloudTarget) waitForRun(signer *RequestSigner, runID int64, timeout time.Duration) error {
	runURL := fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/job/%s-%s/run/%d",
		t.apiURL,
		t.deployment.Application.Tenant, t.deployment.Application.Application, t.deployment.Application.Instance,
		t.deployment.Zone.Environment, t.deployment.Zone.Region, runID)
	req, err := http.NewRequest("GET", runURL, nil)
	if err != nil {
		return err
	}
	lastID := int64(-1)
	requestFunc := func() *http.Request {
		q := req.URL.Query()
		q.Set("after", strconv.FormatInt(lastID, 10))
		req.URL.RawQuery = q.Encode()
		if err := signer.SignRequest(req); err != nil {
			panic(err)
		}
		return req
	}
	jobSuccessFunc := func(status int, response []byte) (bool, error) {
		if status/100 != 2 {
			return false, nil
		}
		var resp jobResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return false, nil
		}
		if t.logOptions.Writer != nil {
			lastID = t.printLog(resp, lastID)
		}
		if resp.Active {
			return false, nil
		}
		if resp.Status != "success" {
			return false, fmt.Errorf("run %d ended with unsuccessful status: %s", runID, resp.Status)
		}
		return true, nil
	}
	_, err = wait(jobSuccessFunc, requestFunc, &t.tlsOptions.KeyPair, timeout)
	return err
}

func (t *cloudTarget) printLog(response jobResponse, last int64) int64 {
	if response.LastID == 0 {
		return last
	}
	var msgs []logMessage
	for step, stepMsgs := range response.Log {
		for _, msg := range stepMsgs {
			if step == "copyVespaLogs" && LogLevel(msg.Type) > t.logOptions.Level {
				continue
			}
			msgs = append(msgs, msg)
		}
	}
	sort.Slice(msgs, func(i, j int) bool { return msgs[i].At < msgs[j].At })
	for _, msg := range msgs {
		tm := time.Unix(msg.At/1000, (msg.At%1000)*1000)
		fmtTime := tm.Format("15:04:05")
		fmt.Fprintf(t.logOptions.Writer, "[%s] %-7s %s\n", fmtTime, msg.Type, msg.Message)
	}
	return response.LastID
}

func (t *cloudTarget) discoverEndpoints(signer *RequestSigner, timeout time.Duration) error {
	deploymentURL := fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s",
		t.apiURL,
		t.deployment.Application.Tenant, t.deployment.Application.Application, t.deployment.Application.Instance,
		t.deployment.Zone.Environment, t.deployment.Zone.Region)
	req, err := http.NewRequest("GET", deploymentURL, nil)
	if err != nil {
		return err
	}
	if err := signer.SignRequest(req); err != nil {
		return err
	}
	var endpointURL string
	endpointFunc := func(status int, response []byte) (bool, error) {
		if status/100 != 2 {
			return false, nil
		}
		var resp deploymentResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return false, nil
		}
		if len(resp.Endpoints) == 0 {
			return false, nil
		}
		endpointURL = resp.Endpoints[0].URL
		return true, nil
	}
	if _, err = wait(endpointFunc, func() *http.Request { return req }, &t.tlsOptions.KeyPair, timeout); err != nil {
		return err
	}
	if endpointURL == "" {
		return fmt.Errorf("no endpoint discovered")
	}
	t.queryURL = endpointURL
	t.documentURL = endpointURL
	return nil
}

// LocalTarget creates a target for a Vespa platform running locally.
func LocalTarget() Target {
	return &customTarget{targetType: localTargetType, baseURL: "http://127.0.0.1"}
}

// CustomTarget creates a Target for a Vespa platform running at baseURL.
func CustomTarget(baseURL string) Target {
	return &customTarget{targetType: customTargetType, baseURL: baseURL}
}

// CloudTarget creates a Target for the Vespa Cloud platform.
func CloudTarget(apiURL string, deployment Deployment, apiKey []byte, tlsOptions TLSOptions, logOptions LogOptions) Target {
	return &cloudTarget{
		apiURL:     apiURL,
		targetType: cloudTargetType,
		deployment: deployment,
		apiKey:     apiKey,
		tlsOptions: tlsOptions,
		logOptions: logOptions,
	}
}

// LogLevel returns an int representing a named log level.
func LogLevel(name string) int {
	switch name {
	case "error":
		return 0
	case "warning":
		return 1
	case "info":
		return 2
	default: // everything else, e.g. debug
		return 3
	}
}

type deploymentEndpoint struct {
	URL string `json:"url"`
}

type deploymentResponse struct {
	Endpoints []deploymentEndpoint `json:"endpoints"`
}

type serviceConvergeResponse struct {
	Converged bool `json:"converged"`
}

type jobResponse struct {
	Active bool                    `json:"active"`
	Status string                  `json:"status"`
	Log    map[string][]logMessage `json:"log"`
	LastID int64                   `json:"lastId"`
}

type logMessage struct {
	At      int64  `json:"at"`
	Type    string `json:"type"`
	Message string `json:"message"`
}

type responseFunc func(status int, response []byte) (bool, error)

type requestFunc func() *http.Request

func wait(fn responseFunc, reqFn requestFunc, certificate *tls.Certificate, timeout time.Duration) (int, error) {
	if certificate != nil {
		util.ActiveHttpClient.UseCertificate(*certificate)
	}
	var (
		httpErr    error
		response   *http.Response
		statusCode int
	)
	deadline := time.Now().Add(timeout)
	loopOnce := timeout == 0
	for time.Now().Before(deadline) || loopOnce {
		response, httpErr = util.HttpDo(reqFn(), 10*time.Second, "")
		if httpErr == nil {
			statusCode = response.StatusCode
			body, err := ioutil.ReadAll(response.Body)
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
		if loopOnce {
			break
		}
		time.Sleep(waitRetryInterval)
	}
	return statusCode, httpErr
}
