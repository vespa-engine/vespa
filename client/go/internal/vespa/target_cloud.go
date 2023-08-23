package vespa

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"net/http"
	"sort"
	"strconv"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
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
	httpClient        util.HTTPClient
	apiAuth           Authenticator
	deploymentAuth    Authenticator
	retryInterval     time.Duration
}

type deploymentEndpoint struct {
	Cluster string `json:"cluster"`
	URL     string `json:"url"`
	Scope   string `json:"scope"`
}

type deploymentResponse struct {
	Endpoints []deploymentEndpoint `json:"endpoints"`
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
func CloudTarget(httpClient util.HTTPClient, apiAuth Authenticator, deploymentAuth Authenticator,
	apiOptions APIOptions, deploymentOptions CloudDeploymentOptions,
	logOptions LogOptions, retryInterval time.Duration) (Target, error) {
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
	var clusterUrls map[string]string
	if t.deploymentOptions.CustomURL != "" {
		// Custom URL is always preferred
		clusterUrls = map[string]string{"": t.deploymentOptions.CustomURL}
	} else if t.deploymentOptions.ClusterURLs != nil {
		// ... then endpoints specified through environment
		clusterUrls = t.deploymentOptions.ClusterURLs
	} else {
		// ... then discovered endpoints
		endpoints, err := t.discoverEndpoints(timeout)
		if err != nil {
			return nil, err
		}
		clusterUrls = endpoints
	}
	services := make([]*Service, 0, len(clusterUrls))
	for name, url := range clusterUrls {
		service := &Service{
			Name:          name,
			BaseURL:       url,
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
	sort.Slice(services, func(i, j int) bool { return services[i].Name < services[j].Name })
	return services, nil
}

func (t *cloudTarget) CheckVersion(clientVersion version.Version) error {
	if clientVersion.IsZero() { // development version is always fine
		return nil
	}
	req, err := http.NewRequest("GET", fmt.Sprintf("%s/cli/v1/", t.apiOptions.System.URL), nil)
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
	if _, err := t.deployServiceWait(logFunc, requestFunc, timeout); err != nil && !errors.Is(err, errWaitTimeout) {
		return fmt.Errorf("failed to read logs: %s", err)
	}
	return nil
}

func (t *cloudTarget) deployServiceWait(fn responseFunc, reqFn requestFunc, timeout time.Duration) (int, error) {
	deployService, err := t.DeployService()
	if err != nil {
		return 0, err
	}
	return wait(deployService, fn, reqFn, timeout, t.retryInterval)
}

func (t *cloudTarget) discoverLatestRun(timeout time.Duration) (int64, error) {
	runsURL := t.apiOptions.System.RunsURL(t.deploymentOptions.Deployment) + "?limit=1"
	req, err := http.NewRequest("GET", runsURL, nil)
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
	_, err = t.deployServiceWait(jobsSuccessFunc, requestFunc, timeout)
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
	req, err := http.NewRequest("GET", runURL, nil)
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
			return false, fmt.Errorf("run %d ended with unsuccessful status: %s", runID, resp.Status)
		}
		success = true
		return success, nil
	}
	_, err = t.deployServiceWait(jobSuccessFunc, requestFunc, timeout)
	if err != nil {
		return 0, fmt.Errorf("deployment run %d incomplete%s: %w", runID, waitDescription(timeout), err)
	}
	if !success {
		return 0, fmt.Errorf("deployment run %d incomplete%s", runID, waitDescription(timeout))
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

func (t *cloudTarget) discoverEndpoints(timeout time.Duration) (map[string]string, error) {
	deploymentURL := fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s",
		t.apiOptions.System.URL,
		t.deploymentOptions.Deployment.Application.Tenant, t.deploymentOptions.Deployment.Application.Application, t.deploymentOptions.Deployment.Application.Instance,
		t.deploymentOptions.Deployment.Zone.Environment, t.deploymentOptions.Deployment.Zone.Region)
	req, err := http.NewRequest("GET", deploymentURL, nil)
	if err != nil {
		return nil, err
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
	if _, err := t.deployServiceWait(endpointFunc, func() *http.Request { return req }, timeout); err != nil {
		return nil, fmt.Errorf("no endpoints found%s: %w", waitDescription(timeout), err)
	}
	if len(urlsByCluster) == 0 {
		return nil, fmt.Errorf("no endpoints found%s", waitDescription(timeout))
	}
	return urlsByCluster, nil
}
