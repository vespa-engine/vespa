// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

type mockVespaApi struct {
	deploymentConverged bool
	authFailure         bool
	serverURL           string
}

func (v *mockVespaApi) mockVespaHandler(w http.ResponseWriter, req *http.Request) {
	if v.authFailure {
		response := `{"message":"unauthorized"}`
		w.WriteHeader(401)
		w.Write([]byte(response))
	}
	switch req.URL.Path {
	case "/cli/v1/":
		response := `{"minVersion":"8.0.0"}`
		w.Write([]byte(response))
	case "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1":
		response := "{}"
		if v.deploymentConverged {
			response = fmt.Sprintf(`{"endpoints": [{"url": "%s","scope": "zone","cluster": "cluster1"}]}`, v.serverURL)
		}
		w.Write([]byte(response))
	case "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/run/42":
		var response string
		if v.deploymentConverged {
			response = `{"active": false, "status": "success"}`
		} else {
			response = `{"active": true, "status": "running",
                         "lastId": 42,
                         "log": {"deployReal": [{"at": 1631707708431,
                                                 "type": "info",
                                                 "message": "Deploying platform version 7.465.17 and application version 1.0.2 ..."}]}}`
		}
		w.Write([]byte(response))
	case "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge":
		response := fmt.Sprintf(`{"converged": %t}`, v.deploymentConverged)
		w.Write([]byte(response))
	case "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1/logs":
		log := `1632738690.905535	host1a.dev.aws-us-east-1c	806/53	logserver-container	Container.com.yahoo.container.jdisc.ConfiguredApplication	info	Switching to the latest deployed set of configurations and components. Application config generation: 52532
1632738698.600189	host1a.dev.aws-us-east-1c	1723/33590	config-sentinel	sentinel.sentinel.config-owner	config	Sentinel got 3 service elements [tenant(vespa-team), application(music), instance(mpolden)] for config generation 52532
`
		w.Write([]byte(log))
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
	lt := LocalTarget(&mock.HTTPClient{}, TLSOptions{})
	assertServiceURL(t, "http://127.0.0.1:19071", lt, "deploy")
	assertServiceURL(t, "http://127.0.0.1:8080", lt, "query")
	assertServiceURL(t, "http://127.0.0.1:8080", lt, "document")

	ct := CustomTarget(&mock.HTTPClient{}, "http://192.0.2.42", TLSOptions{})
	assertServiceURL(t, "http://192.0.2.42", ct, "deploy")
	assertServiceURL(t, "http://192.0.2.42", ct, "query")
	assertServiceURL(t, "http://192.0.2.42", ct, "document")

	ct2 := CustomTarget(&mock.HTTPClient{}, "http://192.0.2.42:60000", TLSOptions{})
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "deploy")
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "query")
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "document")
}

func TestCustomTargetWait(t *testing.T) {
	vc := mockVespaApi{}
	srv := httptest.NewServer(http.HandlerFunc(vc.mockVespaHandler))
	defer srv.Close()
	target := CustomTarget(util.CreateClient(time.Second*10), srv.URL, TLSOptions{})

	_, err := target.Service("query", time.Millisecond, 42, "")
	assert.NotNil(t, err)

	vc.deploymentConverged = true
	_, err = target.Service("query", time.Millisecond, 42, "")
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

	var logWriter bytes.Buffer
	target := createCloudTarget(t, srv.URL, &logWriter)
	vc.authFailure = true
	assertServiceWaitErr(t, 401, true, target, "deploy")
	vc.authFailure = false
	assertServiceWait(t, 200, target, "deploy")

	_, err := target.Service("query", time.Millisecond, 42, "")
	assert.NotNil(t, err)

	vc.deploymentConverged = true
	_, err = target.Service("query", time.Millisecond, 42, "")
	assert.Nil(t, err)

	assertServiceWait(t, 500, target, "query")
	assertServiceWait(t, 500, target, "document")

	// Log timestamp is converted to local time, do the same here in case the local time where tests are run varies
	tm := time.Unix(1631707708, 431000)
	expectedTime := tm.Format("[15:04:05]")
	assert.Equal(t, expectedTime+" info    Deploying platform version 7.465.17 and application version 1.0.2 ...\n", logWriter.String())
}

func TestLog(t *testing.T) {
	vc := mockVespaApi{}
	srv := httptest.NewServer(http.HandlerFunc(vc.mockVespaHandler))
	defer srv.Close()
	vc.serverURL = srv.URL
	vc.deploymentConverged = true

	var buf bytes.Buffer
	target := createCloudTarget(t, srv.URL, io.Discard)
	if err := target.PrintLog(LogOptions{Writer: &buf, Level: 3}); err != nil {
		t.Fatal(err)
	}
	expected := "[2021-09-27 10:31:30.905535] host1a.dev.aws-us-east-1c info    logserver-container Container.com.yahoo.container.jdisc.ConfiguredApplication\tSwitching to the latest deployed set of configurations and components. Application config generation: 52532\n" +
		"[2021-09-27 10:31:38.600189] host1a.dev.aws-us-east-1c config  config-sentinel  sentinel.sentinel.config-owner\tSentinel got 3 service elements [tenant(vespa-team), application(music), instance(mpolden)] for config generation 52532\n"
	assert.Equal(t, expected, buf.String())
}

func TestCheckVersion(t *testing.T) {
	vc := mockVespaApi{}
	srv := httptest.NewServer(http.HandlerFunc(vc.mockVespaHandler))
	defer srv.Close()

	target := createCloudTarget(t, srv.URL, io.Discard)
	assert.Nil(t, target.CheckVersion(version.MustParse("8.0.0")))
	assert.Nil(t, target.CheckVersion(version.MustParse("8.1.0")))
	assert.NotNil(t, target.CheckVersion(version.MustParse("7.0.0")))
}

func createCloudTarget(t *testing.T, url string, logWriter io.Writer) Target {
	apiKey, err := CreateAPIKey()
	assert.Nil(t, err)

	auth := &mockAuthenticator{}
	target, err := CloudTarget(
		util.CreateClient(time.Second*10),
		auth,
		auth,
		APIOptions{APIKey: apiKey, System: PublicSystem},
		CloudDeploymentOptions{
			Deployment: Deployment{
				Application: ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"},
				Zone:        ZoneID{Environment: "dev", Region: "us-north-1"},
			},
		},
		LogOptions{Writer: logWriter},
	)
	if err != nil {
		t.Fatal(err)
	}
	if ct, ok := target.(*cloudTarget); ok {
		ct.apiOptions.System.URL = url
	} else {
		t.Fatalf("Wrong target type %T", ct)
	}
	return target
}

func assertServiceURL(t *testing.T, url string, target Target, service string) {
	s, err := target.Service(service, 0, 42, "")
	assert.Nil(t, err)
	assert.Equal(t, url, s.BaseURL)
}

func assertServiceWait(t *testing.T, expectedStatus int, target Target, service string) {
	assertServiceWaitErr(t, expectedStatus, false, target, service)
}

func assertServiceWaitErr(t *testing.T, expectedStatus int, expectErr bool, target Target, service string) {
	s, err := target.Service(service, 0, 42, "")
	assert.Nil(t, err)

	status, err := s.Wait(0)
	if expectErr {
		assert.NotNil(t, err)
	} else {
		assert.Nil(t, err)
	}
	assert.Equal(t, expectedStatus, status)
}

type mockAuthenticator struct{}

func (a *mockAuthenticator) Authenticate(request *http.Request) error { return nil }
