package vespa

import (
	"crypto/tls"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
)

type mockVespaApi struct {
	deploymentConverged bool
	serverURL           string
}

func (v *mockVespaApi) mockVespaHandler(w http.ResponseWriter, req *http.Request) {
	switch req.URL.Path {
	case "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1":
		response := "{}"
		if v.deploymentConverged {
			response = fmt.Sprintf(`{"endpoints": [{"url": "%s"}]}`, v.serverURL)
		}
		w.Write([]byte(response))
	case "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/run/42":
		var response string
		if v.deploymentConverged {
			response = `{"active": false, "status": "success"}`
		} else {
			response = `{"active": true, "status": "running"}`
		}
		w.Write([]byte(response))
	case "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge":
		response := fmt.Sprintf(`{"converged": %t}`, v.deploymentConverged)
		w.Write([]byte(response))
	case "/status.html":
		w.Write([]byte("OK"))
	case "/ApplicationStatus":
		w.WriteHeader(500)
		w.Write([]byte("Unknown error"))
	default:
		w.WriteHeader(400)
		w.Write([]byte("Invalid path: " + req.URL.Path))
	}
}

func TestCustomTarget(t *testing.T) {
	lt := LocalTarget()
	assertServiceURL(t, "http://127.0.0.1:19071", lt, "deploy")
	assertServiceURL(t, "http://127.0.0.1:8080", lt, "query")
	assertServiceURL(t, "http://127.0.0.1:8080", lt, "document")

	ct := CustomTarget("http://192.0.2.42")
	assertServiceURL(t, "http://192.0.2.42:19071", ct, "deploy")
	assertServiceURL(t, "http://192.0.2.42:8080", ct, "query")
	assertServiceURL(t, "http://192.0.2.42:8080", ct, "document")

	ct2 := CustomTarget("http://192.0.2.42:60000")
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "deploy")
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "query")
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "document")
}

func TestCustomTargetWait(t *testing.T) {
	vc := mockVespaApi{}
	srv := httptest.NewServer(http.HandlerFunc(vc.mockVespaHandler))
	defer srv.Close()
	target := CustomTarget(srv.URL)

	err := target.DiscoverServices(0, 42)
	assert.NotNil(t, err)

	vc.deploymentConverged = true
	err = target.DiscoverServices(0, 42)
	assert.Nil(t, err)

	assertServiceWait(t, 200, target, "deploy")
	assertServiceWait(t, 500, target, "query")
	assertServiceWait(t, 500, target, "document")
}

func TestCloudTargetWait(t *testing.T) {
	vc := mockVespaApi{}
	srv := httptest.NewServer(http.HandlerFunc(vc.mockVespaHandler))
	defer srv.Close()
	vc.serverURL = srv.URL

	kp, err := CreateKeyPair()
	assert.Nil(t, err)

	x509KeyPair, err := tls.X509KeyPair(kp.Certificate, kp.PrivateKey)
	assert.Nil(t, err)
	apiKey, err := CreateAPIKey()
	assert.Nil(t, err)

	target := CloudTarget(
		Deployment{
			Application: ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"},
			Zone:        ZoneID{Environment: "dev", Region: "us-north-1"},
		},
		x509KeyPair,
		apiKey)
	if ct, ok := target.(*cloudTarget); ok {
		ct.cloudAPI = srv.URL
	} else {
		t.Fatalf("Wrong target type %T", ct)
	}
	assertServiceWait(t, 200, target, "deploy")

	_, err = target.Service("query")
	assert.NotNil(t, err)

	err = target.DiscoverServices(0, 42)
	assert.NotNil(t, err)

	vc.deploymentConverged = true
	err = target.DiscoverServices(0, 42)
	assert.Nil(t, err)

	assertServiceWait(t, 500, target, "query")
	assertServiceWait(t, 500, target, "document")
}

func assertServiceURL(t *testing.T, url string, target Target, service string) {
	s, err := target.Service(service)
	assert.Nil(t, err)
	assert.Equal(t, url, s.BaseURL)
}

func assertServiceWait(t *testing.T, expectedStatus int, target Target, service string) {
	s, err := target.Service(service)
	assert.Nil(t, err)

	status, err := s.Wait(0)
	assert.Nil(t, err)
	assert.Equal(t, expectedStatus, status)
}
