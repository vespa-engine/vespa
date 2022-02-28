// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package vespa

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"math"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"time"

	"github.com/vespa-engine/vespa/client/go/auth0"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/version"
	"github.com/vespa-engine/vespa/client/go/zts"
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

	deployService   = "deploy"
	queryService    = "query"
	documentService = "document"

	retryInterval = 2 * time.Second
)

// Service represents a Vespa service.
type Service struct {
	BaseURL    string
	Name       string
	TLSOptions TLSOptions
	ztsClient  ztsClient
}

// Target represents a Vespa platform, running named Vespa services.
type Target interface {
	// Type returns this target's type, e.g. local or cloud.
	Type() string

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

// CloudOptions configures URL and authentication for a cloud target.
type APIOptions struct {
	System         System
	TLSOptions     TLSOptions
	APIKey         []byte
	AuthConfigPath string
}

// CloudDeploymentOptions configures the deployment to manage through a cloud target.
type CloudDeploymentOptions struct {
	Deployment  Deployment
	TLSOptions  TLSOptions
	ClusterURLs map[string]string // Endpoints keyed on cluster name
}

type customTarget struct {
	targetType string
	baseURL    string
}

// Do sends request to this service. Any required authentication happens automatically.
func (s *Service) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	if s.TLSOptions.KeyPair.Certificate != nil {
		util.ActiveHttpClient.UseCertificate([]tls.Certificate{s.TLSOptions.KeyPair})
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

func (t *customTarget) Deployment() Deployment { return Deployment{} }

func (t *customTarget) Service(name string, timeout time.Duration, sessionOrRunID int64, cluster string) (*Service, error) {
	if timeout > 0 && name != deployService {
		if err := t.waitForConvergence(timeout); err != nil {
			return nil, err
		}
	}
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

func (t *customTarget) PrintLog(options LogOptions) error {
	return fmt.Errorf("reading logs from non-cloud deployment is unsupported")
}

func (t *customTarget) SignRequest(req *http.Request, sigKeyId string) error { return nil }

func (t *customTarget) CheckVersion(version version.Version) error { return nil }

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

func (t *customTarget) waitForConvergence(timeout time.Duration) error {
	deployer, err := t.Service(deployService, 0, 0, "")
	if err != nil {
		return err
	}
	url := fmt.Sprintf("%s/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge", deployer.BaseURL)
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
	apiOptions        APIOptions
	deploymentOptions CloudDeploymentOptions
	logOptions        LogOptions
	ztsClient         ztsClient
}

type ztsClient interface {
	AccessToken(domain string, certficiate tls.Certificate) (string, error)
}

func (t *cloudTarget) resolveEndpoint(cluster string) (string, error) {
	if cluster == "" {
		for _, u := range t.deploymentOptions.ClusterURLs {
			if len(t.deploymentOptions.ClusterURLs) == 1 {
				return u, nil
			} else {
				return "", fmt.Errorf("multiple clusters, none chosen: %v", t.deploymentOptions.ClusterURLs)
			}
		}
	} else {
		u := t.deploymentOptions.ClusterURLs[cluster]
		if u == "" {
			clusters := make([]string, len(t.deploymentOptions.ClusterURLs))
			for c := range t.deploymentOptions.ClusterURLs {
				clusters = append(clusters, c)
			}
			return "", fmt.Errorf("unknown cluster '%s': must be one of %v", cluster, clusters)
		}
		return u, nil
	}

	return "", fmt.Errorf("no endpoints")
}

func (t *cloudTarget) Type() string {
	switch t.apiOptions.System.Name {
	case MainSystem.Name, CDSystem.Name:
		return TargetHosted
	}
	return TargetCloud
}

func (t *cloudTarget) Deployment() Deployment { return t.deploymentOptions.Deployment }

func (t *cloudTarget) Service(name string, timeout time.Duration, runID int64, cluster string) (*Service, error) {
	if name != deployService && t.deploymentOptions.ClusterURLs == nil {
		if err := t.waitForEndpoints(timeout, runID); err != nil {
			return nil, err
		}
	}
	switch name {
	case deployService:
		return &Service{Name: name, BaseURL: t.apiOptions.System.URL, TLSOptions: t.apiOptions.TLSOptions, ztsClient: t.ztsClient}, nil
	case queryService, documentService:
		url, err := t.resolveEndpoint(cluster)
		if err != nil {
			return nil, err
		}
		t.deploymentOptions.TLSOptions.AthenzDomain = t.apiOptions.System.AthenzDomain
		return &Service{Name: name, BaseURL: url, TLSOptions: t.deploymentOptions.TLSOptions, ztsClient: t.ztsClient}, nil
	}
	return nil, fmt.Errorf("unknown service: %s", name)
}

func (t *cloudTarget) SignRequest(req *http.Request, keyID string) error {
	if t.apiOptions.System.IsPublic() {
		if t.apiOptions.APIKey != nil {
			signer := NewRequestSigner(keyID, t.apiOptions.APIKey)
			return signer.SignRequest(req)
		} else {
			return t.addAuth0AccessToken(req)
		}
	} else {
		if t.apiOptions.TLSOptions.KeyPair.Certificate == nil {
			return fmt.Errorf("system %s requires a certificate for authentication", t.apiOptions.System.Name)
		}
		return nil
	}
}

func (t *cloudTarget) CheckVersion(clientVersion version.Version) error {
	if clientVersion.IsZero() { // development version is always fine
		return nil
	}
	req, err := http.NewRequest("GET", fmt.Sprintf("%s/cli/v1/", t.apiOptions.System.URL), nil)
	if err != nil {
		return err
	}
	response, err := util.HttpDo(req, 10*time.Second, "")
	if err != nil {
		return err
	}
	defer response.Body.Close()
	var cliResponse struct {
		MinVersion string `json:"minVersion"`
	}
	dec := json.NewDecoder(response.Body)
	if err := dec.Decode(&cliResponse); err != nil {
		return err
	}
	minVersion, err := version.Parse(cliResponse.MinVersion)
	if err != nil {
		return err
	}
	if clientVersion.Less(minVersion) {
		return fmt.Errorf("client version %s is less than the minimum supported version: %s", clientVersion, minVersion)
	}
	return nil
}

func (t *cloudTarget) addAuth0AccessToken(request *http.Request) error {
	a, err := auth0.GetAuth0(t.apiOptions.AuthConfigPath, t.apiOptions.System.Name, t.apiOptions.System.URL)
	if err != nil {
		return err
	}
	system, err := a.PrepareSystem(auth0.ContextWithCancel())
	if err != nil {
		return err
	}
	request.Header.Set("Authorization", "Bearer "+system.AccessToken)
	return nil
}

func (t *cloudTarget) logsURL() string {
	return fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s/logs",
		t.apiOptions.System.URL,
		t.deploymentOptions.Deployment.Application.Tenant, t.deploymentOptions.Deployment.Application.Application, t.deploymentOptions.Deployment.Application.Instance,
		t.deploymentOptions.Deployment.Zone.Environment, t.deploymentOptions.Deployment.Zone.Region)
}

func (t *cloudTarget) PrintLog(options LogOptions) error {
	req, err := http.NewRequest("GET", t.logsURL(), nil)
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
		t.SignRequest(req, t.deploymentOptions.Deployment.Application.SerializedForm())
		return req
	}
	logFunc := func(status int, response []byte) (bool, error) {
		if ok, err := isOK(status); !ok {
			return ok, err
		}
		logEntries, err := ReadLogEntries(bytes.NewReader(response))
		if err != nil {
			return true, err
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
	_, err = wait(logFunc, requestFunc, &t.apiOptions.TLSOptions.KeyPair, timeout)
	return err
}

func (t *cloudTarget) waitForEndpoints(timeout time.Duration, runID int64) error {
	if runID > 0 {
		if err := t.waitForRun(runID, timeout); err != nil {
			return err
		}
	}
	return t.discoverEndpoints(timeout)
}

func (t *cloudTarget) waitForRun(runID int64, timeout time.Duration) error {
	runURL := fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/job/%s-%s/run/%d",
		t.apiOptions.System.URL,
		t.deploymentOptions.Deployment.Application.Tenant, t.deploymentOptions.Deployment.Application.Application, t.deploymentOptions.Deployment.Application.Instance,
		t.deploymentOptions.Deployment.Zone.Environment, t.deploymentOptions.Deployment.Zone.Region, runID)
	req, err := http.NewRequest("GET", runURL, nil)
	if err != nil {
		return err
	}
	lastID := int64(-1)
	requestFunc := func() *http.Request {
		q := req.URL.Query()
		q.Set("after", strconv.FormatInt(lastID, 10))
		req.URL.RawQuery = q.Encode()
		if err := t.SignRequest(req, t.deploymentOptions.Deployment.Application.SerializedForm()); err != nil {
			panic(err)
		}
		return req
	}
	jobSuccessFunc := func(status int, response []byte) (bool, error) {
		if ok, err := isOK(status); !ok {
			return ok, err
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
	_, err = wait(jobSuccessFunc, requestFunc, &t.apiOptions.TLSOptions.KeyPair, timeout)
	return err
}

func (t *cloudTarget) printLog(response jobResponse, last int64) int64 {
	if response.LastID == 0 {
		return last
	}
	var msgs []logMessage
	for step, stepMsgs := range response.Log {
		for _, msg := range stepMsgs {
			if step == "copyVespaLogs" && LogLevel(msg.Type) > t.logOptions.Level || LogLevel(msg.Type) == 3 {
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

func (t *cloudTarget) discoverEndpoints(timeout time.Duration) error {
	deploymentURL := fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s",
		t.apiOptions.System.URL,
		t.deploymentOptions.Deployment.Application.Tenant, t.deploymentOptions.Deployment.Application.Application, t.deploymentOptions.Deployment.Application.Instance,
		t.deploymentOptions.Deployment.Zone.Environment, t.deploymentOptions.Deployment.Zone.Region)
	req, err := http.NewRequest("GET", deploymentURL, nil)
	if err != nil {
		return err
	}
	if err := t.SignRequest(req, t.deploymentOptions.Deployment.Application.SerializedForm()); err != nil {
		return err
	}
	urlsByCluster := make(map[string]string)
	endpointFunc := func(status int, response []byte) (bool, error) {
		if ok, err := isOK(status); !ok {
			return ok, err
		}
		var resp deploymentResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return false, nil
		}
		if len(resp.Endpoints) == 0 {
			return false, nil
		}
		for _, endpoint := range resp.Endpoints {
			if endpoint.Scope != "zone" {
				continue
			}
			urlsByCluster[endpoint.Cluster] = endpoint.URL
		}
		return true, nil
	}
	if _, err = wait(endpointFunc, func() *http.Request { return req }, &t.apiOptions.TLSOptions.KeyPair, timeout); err != nil {
		return err
	}
	if len(urlsByCluster) == 0 {
		return fmt.Errorf("no endpoints discovered")
	}
	t.deploymentOptions.ClusterURLs = urlsByCluster
	return nil
}

func isOK(status int) (bool, error) {
	if status == 401 {
		return false, fmt.Errorf("status %d: invalid api key", status)
	}
	return status/100 == 2, nil
}

// LocalTarget creates a target for a Vespa platform running locally.
func LocalTarget() Target {
	return &customTarget{targetType: TargetLocal, baseURL: "http://127.0.0.1"}
}

// CustomTarget creates a Target for a Vespa platform running at baseURL.
func CustomTarget(baseURL string) Target {
	return &customTarget{targetType: TargetCustom, baseURL: baseURL}
}

// CloudTarget creates a Target for the Vespa Cloud or hosted Vespa platform.
func CloudTarget(apiOptions APIOptions, deploymentOptions CloudDeploymentOptions, logOptions LogOptions) (Target, error) {
	ztsClient, err := zts.NewClient(zts.DefaultURL, util.ActiveHttpClient)
	if err != nil {
		return nil, err
	}
	return &cloudTarget{
		apiOptions:        apiOptions,
		deploymentOptions: deploymentOptions,
		logOptions:        logOptions,
		ztsClient:         ztsClient,
	}, nil
}

type deploymentEndpoint struct {
	Cluster string `json:"cluster"`
	URL     string `json:"url"`
	Scope   string `json:"scope"`
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
		util.ActiveHttpClient.UseCertificate([]tls.Certificate{*certificate})
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
		response, httpErr = util.HttpDo(req, 10*time.Second, "")
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
		timeLeft := time.Until(deadline)
		if loopOnce || timeLeft < retryInterval {
			break
		}
		time.Sleep(retryInterval)
	}
	return statusCode, httpErr
}
