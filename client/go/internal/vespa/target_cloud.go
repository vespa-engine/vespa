package vespa

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
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
	ClusterURLs map[string]string // Endpoints keyed on cluster name
}

type cloudTarget struct {
	apiOptions        APIOptions
	deploymentOptions CloudDeploymentOptions
	logOptions        LogOptions
	httpClient        util.HTTPClient
	apiAuth           Authenticator
	deploymentAuth    Authenticator
}

type deploymentEndpoint struct {
	Cluster string `json:"cluster"`
	URL     string `json:"url"`
	Scope   string `json:"scope"`
}

type deploymentResponse struct {
	Endpoints []deploymentEndpoint `json:"endpoints"`
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

// CloudTarget creates a Target for the Vespa Cloud or hosted Vespa platform.
func CloudTarget(httpClient util.HTTPClient, apiAuth Authenticator, deploymentAuth Authenticator, apiOptions APIOptions, deploymentOptions CloudDeploymentOptions, logOptions LogOptions) (Target, error) {
	return &cloudTarget{
		httpClient:        httpClient,
		apiOptions:        apiOptions,
		deploymentOptions: deploymentOptions,
		logOptions:        logOptions,
		apiAuth:           apiAuth,
		deploymentAuth:    deploymentAuth,
	}, nil
}

func (t *cloudTarget) findClusterURL(cluster string) (string, error) {
	clusters := make([]string, 0, len(t.deploymentOptions.ClusterURLs))
	for c := range t.deploymentOptions.ClusterURLs {
		clusters = append(clusters, c)
	}
	if cluster == "" {
		for _, url := range t.deploymentOptions.ClusterURLs {
			if len(t.deploymentOptions.ClusterURLs) == 1 {
				return url, nil
			} else {
				return "", fmt.Errorf("no cluster specified: found multiple clusters '%s'", strings.Join(clusters, "', '"))
			}
		}
	} else {
		url, ok := t.deploymentOptions.ClusterURLs[cluster]
		if !ok {
			return "", fmt.Errorf("invalid cluster '%s': must be one of '%s'", cluster, strings.Join(clusters, "', '"))
		}
		return url, nil
	}
	return "", fmt.Errorf("no endpoints found")
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

func (t *cloudTarget) Service(name string, timeout time.Duration, runID int64, cluster string) (*Service, error) {
	switch name {
	case DeployService:
		service := &Service{
			Name:       name,
			BaseURL:    t.apiOptions.System.URL,
			TLSOptions: t.apiOptions.TLSOptions,
			httpClient: t.httpClient,
			auth:       t.apiAuth,
		}
		if timeout > 0 {
			status, err := service.Wait(timeout)
			if err != nil {
				return nil, err
			}
			if ok, _ := isOK(status); !ok {
				return nil, fmt.Errorf("got status %d from deploy service at %s", status, service.BaseURL)
			}
		}
		return service, nil
	case QueryService, DocumentService:
		if t.deploymentOptions.ClusterURLs == nil {
			if err := t.waitForEndpoints(timeout, runID); err != nil {
				return nil, err
			}
		}
		url, err := t.findClusterURL(cluster)
		if err != nil {
			return nil, err
		}
		return &Service{
			Name:       name,
			BaseURL:    url,
			TLSOptions: t.deploymentOptions.TLSOptions,
			httpClient: t.httpClient,
			auth:       t.deploymentAuth,
		}, nil
	default:
		return nil, fmt.Errorf("unknown service: %s", name)
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
	deployService, err := t.Service(DeployService, 0, 0, "")
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
	_, err = t.deployServiceWait(logFunc, requestFunc, timeout)
	return err
}

func (t *cloudTarget) deployServiceWait(fn responseFunc, reqFn requestFunc, timeout time.Duration) (int, error) {
	deployService, err := t.Service(DeployService, 0, 0, "")
	if err != nil {
		return 0, err
	}
	return wait(deployService, fn, reqFn, timeout)
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
	_, err = t.deployServiceWait(jobSuccessFunc, requestFunc, timeout)
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
		return err
	}
	if len(urlsByCluster) == 0 {
		return fmt.Errorf("no endpoints discovered for %s", t.deploymentOptions.Deployment)
	}
	t.deploymentOptions.ClusterURLs = urlsByCluster
	return nil
}
