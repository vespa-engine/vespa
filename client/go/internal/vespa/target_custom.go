package vespa

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

type customTarget struct {
	targetType    string
	baseURL       string
	httpClient    util.HTTPClient
	tlsOptions    TLSOptions
	retryInterval time.Duration
}

type serviceStatus struct {
	Converged         bool          `json:"converged"`
	CurrentGeneration int64         `json:"currentGeneration"`
	Services          []serviceInfo `json:"services"`
}

type serviceInfo struct {
	ClusterName string `json:"clusterName"`
	Type        string `json:"type"`
	Port        int    `json:"port"`
}

// LocalTarget creates a target for a Vespa platform running locally.
func LocalTarget(httpClient util.HTTPClient, tlsOptions TLSOptions, retryInterval time.Duration) Target {
	return &customTarget{
		targetType:    TargetLocal,
		baseURL:       "http://127.0.0.1",
		httpClient:    httpClient,
		tlsOptions:    tlsOptions,
		retryInterval: retryInterval,
	}
}

// CustomTarget creates a Target for a Vespa platform running at baseURL.
func CustomTarget(httpClient util.HTTPClient, baseURL string, tlsOptions TLSOptions, retryInterval time.Duration) Target {
	return &customTarget{
		targetType:    TargetCustom,
		baseURL:       baseURL,
		httpClient:    httpClient,
		tlsOptions:    tlsOptions,
		retryInterval: retryInterval,
	}
}

func (t *customTarget) Type() string { return t.targetType }

func (t *customTarget) IsCloud() bool { return false }

func (t *customTarget) Deployment() Deployment { return DefaultDeployment }

func (t *customTarget) PrintLog(options LogOptions) error {
	return fmt.Errorf("log access is only supported on cloud: run vespa-logfmt on the admin node instead, or export from a container image (here named 'vespa') using docker exec vespa vespa-logfmt")
}

func (t *customTarget) CheckVersion(version version.Version) error { return nil }

func (t *customTarget) newService(url, name string, deployAPI bool) *Service {
	return &Service{
		BaseURL:       url,
		Name:          name,
		deployAPI:     deployAPI,
		httpClient:    t.httpClient,
		TLSOptions:    t.tlsOptions,
		retryInterval: t.retryInterval,
	}
}

func (t *customTarget) DeployService() (*Service, error) {
	if t.targetType == TargetCustom {
		return t.newService(t.baseURL, "", true), nil
	}
	u, err := t.urlWithPort(19071)
	if err != nil {
		return nil, err
	}
	return t.newService(u.String(), "", true), nil
}

func (t *customTarget) ContainerServices(timeout time.Duration) ([]*Service, error) {
	if t.targetType == TargetCustom {
		return []*Service{t.newService(t.baseURL, "", false)}, nil
	}
	status, err := t.serviceStatus(AnyDeployment, timeout)
	if err != nil {
		return nil, err
	}
	portsByCluster := make(map[string]int)
	for _, serviceInfo := range status.Services {
		if serviceInfo.Type != "container" {
			continue
		}
		clusterName := serviceInfo.ClusterName
		if clusterName == "" { // Vespa version older than 8.206.1, which does not include cluster name in the API
			clusterName = serviceInfo.Type + strconv.Itoa(serviceInfo.Port)
		}
		portsByCluster[clusterName] = serviceInfo.Port
	}
	var services []*Service
	for cluster, port := range portsByCluster {
		url, err := t.urlWithPort(port)
		if err != nil {
			return nil, err
		}
		service := t.newService(url.String(), cluster, false)
		services = append(services, service)
	}
	sort.Slice(services, func(i, j int) bool { return services[i].Name < services[j].Name })
	return services, nil
}

func (t *customTarget) AwaitDeployment(generation int64, timeout time.Duration) (int64, error) {
	status, err := t.serviceStatus(generation, timeout)
	if err != nil {
		return 0, err
	}
	return status.CurrentGeneration, nil
}

func (t *customTarget) urlWithPort(port int) (*url.URL, error) {
	u, err := url.Parse(t.baseURL)
	if err != nil {
		return nil, err
	}
	if _, _, err := net.SplitHostPort(u.Host); err == nil {
		return nil, fmt.Errorf("url %s already contains port", u)
	}
	u.Host = net.JoinHostPort(u.Host, strconv.Itoa(port))
	return u, nil
}

func (t *customTarget) serviceStatus(wantedGeneration int64, timeout time.Duration) (serviceStatus, error) {
	deployService, err := t.DeployService()
	if err != nil {
		return serviceStatus{}, err
	}
	url := fmt.Sprintf("%s/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge", deployService.BaseURL)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return serviceStatus{}, err
	}
	var status serviceStatus
	converged := false
	convergedFunc := func(httpStatus int, response []byte) (bool, error) {
		if ok, err := isOK(httpStatus); !ok {
			return ok, err
		}
		if err := json.Unmarshal(response, &status); err != nil {
			return false, err
		}
		converged = wantedGeneration == AnyDeployment ||
			(wantedGeneration == LatestDeployment && status.Converged) ||
			status.CurrentGeneration == wantedGeneration
		return converged, nil
	}
	if _, err := wait(deployService, convergedFunc, func() *http.Request { return req }, timeout, t.retryInterval); err != nil {
		return serviceStatus{}, fmt.Errorf("deployment not converged%s%s: %w", generationDescription(wantedGeneration), waitDescription(timeout), err)
	}
	if !converged {
		return serviceStatus{}, fmt.Errorf("deployment not converged%s%s", generationDescription(wantedGeneration), waitDescription(timeout))
	}
	return status, nil
}

func generationDescription(generation int64) string {
	switch generation {
	case AnyDeployment:
		return ""
	case LatestDeployment:
		return " on latest generation"
	default:
		return fmt.Sprintf(" on generation %d", generation)
	}
}
