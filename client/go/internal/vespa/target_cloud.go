// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"encoding/json"
	"fmt"
	"net/http"
	"sort"
	"strconv"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

// CloudOptions configures URL and authentication for a cloud target.
type APIOptions struct {
	System     System
	TLSOptions TLSOptions
	APIKey     []byte
}

// CloudDeploymentOptions configures the deployment to manage through a cloud target.
type CloudDeploymentOptions struct {
	Deployment  Deployment
	TLSOptions  TLSOptions
	CustomURL   string
	ClusterURLs map[string]string // Endpoints keyed on cluster name
}

type cloudTarget struct {
	apiOptions        APIOptions
	deploymentOptions CloudDeploymentOptions
	logOptions        LogOptions
	httpClient        httputil.Client
	apiAuth           Authenticator
	deploymentAuth    Authenticator
	retryInterval     time.Duration
}

type deploymentEndpoint struct {
	Cluster    string `json:"cluster"`
	URL        string `json:"url"`
	Scope      string `json:"scope"`
	AuthMethod string `json:"authMethod"`
}

type deploymentResponse struct {
	Endpoints []deploymentEndpoint `json:"endpoints"`
}

type clusterTarget struct {
	URL        string
	AuthMethod string
}

type runResponse struct {
	Active bool                    `json:"active"`
	Status string                  `json:"status"`
	Log    map[string][]logMessage `json:"log"`
	LastID int64                   `json:"lastId"`
}

type jobResponse struct {
	ID int64 `json:"id"`
}

type jobsResponse struct {
	Runs []jobResponse `json:"runs"`
}

type logMessage struct {
	At      int64  `json:"at"`
	Type    string `json:"type"`
	Message string `json:"message"`
}

// CloudTarget creates a Target for the Vespa Cloud or hosted Vespa platform.
func CloudTarget(httpClient httputil.Client, apiAuth Authenticator, deploymentAuth Authenticator,
	apiOptions APIOptions, deploymentOptions CloudDeploymentOptions,
	logOptions LogOptions, retryInterval time.Duration,
) (Target, error) {
	return &cloudTarget{
		httpClient:        httpClient,
		apiOptions:        apiOptions,
		deploymentOptions: deploymentOptions,
		logOptions:        logOptions,
		apiAuth:           apiAuth,
		deploymentAuth:    deploymentAuth,
		retryInterval:     retryInterval,
	}, nil
}

func (t *cloudTarget) Type() string {
	switch t.apiOptions.System.Name {
	case MainSystem.Name, CDSystem.Name:
		return TargetHosted
	}
	return TargetCloud
}

func (t *cloudTarget) IsCloud() bool { return true }

func (t *cloudTarget) Deployment() Deployment { return t.deploymentOptions.Deployment }

func (t *cloudTarget) DeployService() (*Service, error) {
	return &Service{
		BaseURL:       t.apiOptions.System.URL,
		TLSOptions:    t.apiOptions.TLSOptions,
		deployAPI:     true,
		httpClient:    t.httpClient,
		auth:          t.apiAuth,
		retryInterval: t.retryInterval,
	}, nil
}

func (t *cloudTarget) ContainerServices(timeout time.Duration) ([]*Service, error) {
	var clusterTargets map[string][]clusterTarget
	if t.deploymentOptions.CustomURL != "" {
		// Custom URL is always preferred
		clusterTargets = map[string][]clusterTarget{
			"": {
				clusterTarget{URL: t.deploymentOptions.CustomURL, AuthMethod: "mtls"},
				clusterTarget{URL: t.deploymentOptions.CustomURL, AuthMethod: "token"},
			},
		}
	} else if t.deploymentOptions.ClusterURLs != nil {
		// ... then endpoints specified through environment
		clusterTargets = make(map[string][]clusterTarget)
		for cluster, url := range t.deploymentOptions.ClusterURLs {
			clusterTargets[cluster] = []clusterTarget{
				{URL: url, AuthMethod: "mtls"},
				{URL: url, AuthMethod: "token"},
			}
		}
	} else {
		// ... then discovered endpoints
		endpoints, err := t.discoverEndpoints(timeout)
		if err != nil {
			return nil, err
		}
		clusterTargets = endpoints
	}
	services := make([]*Service, 0, len(clusterTargets))
	for name, targets := range clusterTargets {
		for _, target := range targets {
			service := &Service{
				Name:          name,
				BaseURL:       target.URL,
				AuthMethod:    target.AuthMethod,
				TLSOptions:    t.deploymentOptions.TLSOptions,
				httpClient:    t.httpClient,
				auth:          t.deploymentAuth,
				retryInterval: t.retryInterval,
			}
			if timeout > 0 {
				if err := service.Wait(timeout); err != nil {
					return nil, err
				}
			}
			services = append(services, service)
		}
	}
	sort.Slice(services, func(i, j int) bool { return services[i].Name < services[j].Name })
	return services, nil
}

func (t *cloudTarget) CompatibleWith(clientVersion version.Version) error {
	if clientVersion.IsZero() { // development version is always fine
		return nil
	}
	req, err := http.NewRequest(http.MethodGet, fmt.Sprintf("%s/cli/v1/", t.apiOptions.System.URL), nil)
	if err != nil {
		return err
	}
	deployService, err := t.DeployService()
	if err != nil {
		return err
	}
	response, err := deployService.Do(req, 10*time.Second)
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

func (t *cloudTarget) logsURL() string {
	return fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s/logs",
		t.apiOptions.System.URL,
		t.deploymentOptions.Deployment.Application.Tenant, t.deploymentOptions.Deployment.Application.Application, t.deploymentOptions.Deployment.Application.Instance,
		t.deploymentOptions.Deployment.Zone.Environment, t.deploymentOptions.Deployment.Zone.Region)
}

func (t *cloudTarget) PrintLog(options LogOptions) error {
	return pollLogs(t, t.logsURL(), options, t.retryInterval)
}

func (t *cloudTarget) discoverLatestRun(timeout time.Duration) (int64, error) {
	runsURL := t.apiOptions.System.RunsURL(t.deploymentOptions.Deployment) + "?limit=1"
	req, err := http.NewRequest(http.MethodGet, runsURL, nil)
	if err != nil {
		return 0, err
	}
	requestFunc := func() *http.Request { return req }
	var lastRunID int64
	jobsSuccessFunc := func(status int, response []byte) (bool, error) {
		if ok, err := isOK(status); !ok {
			return ok, err
		}
		var resp jobsResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return false, err
		}
		if len(resp.Runs) > 0 {
			lastRunID = resp.Runs[0].ID
			return true, nil
		}
		return false, nil
	}
	_, err = deployRequest(t, jobsSuccessFunc, requestFunc, timeout, t.retryInterval)
	return lastRunID, err
}

func (t *cloudTarget) AwaitDeployment(runID int64, timeout time.Duration) (int64, error) {
	if runID == LatestDeployment {
		lastRunID, err := t.discoverLatestRun(timeout)
		if err != nil {
			return 0, err
		}
		runID = lastRunID
	}
	runURL := t.apiOptions.System.RunURL(t.deploymentOptions.Deployment, runID)
	req, err := http.NewRequest(http.MethodGet, runURL, nil)
	if err != nil {
		return 0, err
	}
	lastID := int64(-1)
	requestFunc := func() *http.Request {
		q := req.URL.Query()
		q.Set("after", strconv.FormatInt(lastID, 10))
		req.URL.RawQuery = q.Encode()
		return req
	}
	success := false
	jobSuccessFunc := func(status int, response []byte) (bool, error) {
		if ok, err := isOK(status); !ok {
			return ok, err
		}
		var resp runResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return false, err
		}
		if t.logOptions.Writer != nil {
			lastID = t.printLog(resp, lastID)
		}
		if resp.Active {
			return false, nil
		}
		if resp.Status != "success" {
			return false, fmt.Errorf("%w: run %d ended with unsuccessful status: %s", ErrDeployment, runID, resp.Status)
		}
		success = true
		return success, nil
	}
	_, err = deployRequest(t, jobSuccessFunc, requestFunc, timeout, t.retryInterval)
	if err != nil {
		return runID, err
	}
	if !success {
		return runID, fmt.Errorf("deployment run %d not yet complete%s", runID, waitDescription(timeout))
	}
	return runID, nil
}

func (t *cloudTarget) printLog(response runResponse, last int64) int64 {
	if response.LastID == 0 {
		return last
	}
	var msgs []logMessage
	for step, stepMsgs := range response.Log {
		for _, msg := range stepMsgs {
			if (step == "copyVespaLogs" && LogLevel(msg.Type) > t.logOptions.Level) || LogLevel(msg.Type) == 3 {
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

func (t *cloudTarget) discoverEndpoints(timeout time.Duration) (map[string][]clusterTarget, error) {
	deploymentURL := fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s",
		t.apiOptions.System.URL,
		t.deploymentOptions.Deployment.Application.Tenant, t.deploymentOptions.Deployment.Application.Application, t.deploymentOptions.Deployment.Application.Instance,
		t.deploymentOptions.Deployment.Zone.Environment, t.deploymentOptions.Deployment.Zone.Region)
	req, err := http.NewRequest(http.MethodGet, deploymentURL, nil)
	if err != nil {
		return nil, err
	}
	clusterTargets := make(map[string][]clusterTarget)
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
			if _, exists := clusterTargets[endpoint.Cluster]; !exists {
				clusterTargets[endpoint.Cluster] = []clusterTarget{}
			}
			target := clusterTarget{URL: endpoint.URL, AuthMethod: endpoint.AuthMethod}
			clusterTargets[endpoint.Cluster] = append(clusterTargets[endpoint.Cluster], target)
		}
		return true, nil
	}
	if _, err := deployRequest(t, endpointFunc, func() *http.Request { return req }, timeout, t.retryInterval); err != nil {
		return nil, fmt.Errorf("no endpoints found in zone %s%s: %w", t.deploymentOptions.Deployment.Zone, waitDescription(timeout), err)
	}
	if len(clusterTargets) == 0 {
		return nil, fmt.Errorf("no endpoints found in zone %s%s", t.deploymentOptions.Deployment.Zone, waitDescription(timeout))
	}
	return clusterTargets, nil
}
