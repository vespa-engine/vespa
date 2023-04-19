package vespa

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

type customTarget struct {
	targetType string
	baseURL    string
	httpClient util.HTTPClient
	tlsOptions TLSOptions
}

type serviceConvergeResponse struct {
	Converged bool `json:"converged"`
}

// LocalTarget creates a target for a Vespa platform running locally.
func LocalTarget(httpClient util.HTTPClient, tlsOptions TLSOptions) Target {
	return &customTarget{targetType: TargetLocal, baseURL: "http://127.0.0.1", httpClient: httpClient, tlsOptions: tlsOptions}
}

// CustomTarget creates a Target for a Vespa platform running at baseURL.
func CustomTarget(httpClient util.HTTPClient, baseURL string, tlsOptions TLSOptions) Target {
	return &customTarget{targetType: TargetCustom, baseURL: baseURL, httpClient: httpClient, tlsOptions: tlsOptions}
}

func (t *customTarget) Type() string { return t.targetType }

func (t *customTarget) IsCloud() bool { return false }

func (t *customTarget) Deployment() Deployment { return Deployment{} }

func (t *customTarget) createService(name string) (*Service, error) {
	switch name {
	case DeployService, QueryService, DocumentService:
		url, err := t.urlWithPort(name)
		if err != nil {
			return nil, err
		}
		return &Service{BaseURL: url, Name: name, httpClient: t.httpClient, TLSOptions: t.tlsOptions}, nil
	}
	return nil, fmt.Errorf("unknown service: %s", name)
}

func (t *customTarget) Service(name string, timeout time.Duration, sessionOrRunID int64, cluster string) (*Service, error) {
	service, err := t.createService(name)
	if err != nil {
		return nil, err
	}
	if timeout > 0 {
		if name == DeployService {
			status, err := service.Wait(timeout)
			if err != nil {
				return nil, err
			}
			if ok, _ := isOK(status); !ok {
				return nil, fmt.Errorf("got status %d from deploy service at %s", status, service.BaseURL)
			}
		} else {
			if err := t.waitForConvergence(timeout); err != nil {
				return nil, err
			}
		}
	}
	return service, nil
}

func (t *customTarget) PrintLog(options LogOptions) error {
	return fmt.Errorf("log access is only supported on cloud: run vespa-logfmt on the admin node instead")
}

func (t *customTarget) CheckVersion(version version.Version) error { return nil }

func (t *customTarget) urlWithPort(serviceName string) (string, error) {
	u, err := url.Parse(t.baseURL)
	if err != nil {
		return "", err
	}
	port := u.Port()
	if port == "" {
		switch serviceName {
		case DeployService:
			port = "19071"
		case QueryService, DocumentService:
			port = "8080"
		default:
			return "", fmt.Errorf("unknown service: %s", serviceName)
		}
		u.Host = u.Host + ":" + port
	}
	return u.String(), nil
}

func (t *customTarget) waitForConvergence(timeout time.Duration) error {
	deployService, err := t.createService(DeployService)
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
		if ok, err := isOK(status); !ok {
			return ok, err
		}
		var resp serviceConvergeResponse
		if err := json.Unmarshal(response, &resp); err != nil {
			return false, nil
		}
		converged = resp.Converged
		return converged, nil
	}
	if _, err := wait(deployService, convergedFunc, func() *http.Request { return req }, timeout); err != nil {
		return err
	}
	if !converged {
		return fmt.Errorf("services have not converged")
	}
	return nil
}
