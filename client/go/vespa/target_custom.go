package vespa

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/vespa-engine/vespa/client/go/version"
)

type customTarget struct {
	targetType string
	baseURL    string
}

type serviceConvergeResponse struct {
	Converged bool `json:"converged"`
}

// LocalTarget creates a target for a Vespa platform running locally.
func LocalTarget() Target {
	return &customTarget{targetType: TargetLocal, baseURL: "http://127.0.0.1"}
}

// CustomTarget creates a Target for a Vespa platform running at baseURL.
func CustomTarget(baseURL string) Target {
	return &customTarget{targetType: TargetCustom, baseURL: baseURL}
}

func (t *customTarget) Type() string { return t.targetType }

func (t *customTarget) Deployment() Deployment { return Deployment{} }

func (t *customTarget) Service(name string, timeout time.Duration, sessionOrRunID int64, cluster string) (*Service, error) {
	if timeout > 0 && name != DeployService {
		if err := t.waitForConvergence(timeout); err != nil {
			return nil, err
		}
	}
	switch name {
	case DeployService, QueryService, DocumentService:
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
	deployer, err := t.Service(DeployService, 0, 0, "")
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
