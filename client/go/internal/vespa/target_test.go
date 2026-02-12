// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"bytes"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

func TestLocalTarget(t *testing.T) {
	// Local target uses discovery
	client := &mock.HTTPClient{}
	lt := LocalTarget(client, TLSOptions{}, 0)
	assertServiceURL(t, "http://127.0.0.1:19071", lt, "deploy")
	for range 2 {
		response := `
{
  "services": [
    {
      "host": "foo",
      "port": 8080,
      "type": "container",
      "url": "http://localhost:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge/localhost:8080",
      "currentGeneration": 1
    },
    {
      "host": "bar",
      "port": 8080,
      "type": "container",
      "url": "http://localhost:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge/localhost:8080",
      "currentGeneration": 1
    },
    {
      "clusterName": "feed",
      "host": "localhost",
      "port": 8081,
      "type": "container",
      "url": "http://localhost:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge/localhost:8081",
      "currentGeneration": 1
    },
    {
      "host": "localhost",
      "port": 19112,
      "type": "searchnode",
      "url": "http://localhost:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge/localhost:19112",
      "currentGeneration": 1
    }
  ],
  "currentGeneration": 1
}`
		client.NextResponse(mock.HTTPResponse{
			URI:    "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge",
			Status: 200,
			Body:   []byte(response),
		})
	}
	assertServiceURL(t, "http://127.0.0.1:8080", lt, "container8080")
	assertServiceURL(t, "http://127.0.0.1:8081", lt, "feed")
}

func TestCustomTarget(t *testing.T) {
	// Custom target always uses URL directly, without discovery
	ct := CustomTarget(&mock.HTTPClient{}, "http://192.0.2.42", TLSOptions{}, 0)
	assertServiceURL(t, "http://192.0.2.42", ct, "deploy")
	assertServiceURL(t, "http://192.0.2.42", ct, "")
	ct2 := CustomTarget(&mock.HTTPClient{}, "http://192.0.2.42:60000", TLSOptions{}, 0)
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "deploy")
	assertServiceURL(t, "http://192.0.2.42:60000", ct2, "")
}

func TestCustomTargetWait(t *testing.T) {
	client := &mock.HTTPClient{}
	target := CustomTarget(client, "http://192.0.2.42", TLSOptions{}, 0)
	// Fails once
	client.NextStatus(500)
	assertService(t, true, target, "", 0)
	// Fails multiple times
	for range 3 {
		client.NextStatus(500)
		client.NextResponseError(io.EOF)
	}
	// Then succeeds
	client.NextResponse(mock.HTTPResponse{URI: "/status.html", Status: 200})
	assertService(t, false, target, "", time.Second)
}

func TestCustomTargetAwaitDeployment(t *testing.T) {
	client := &mock.HTTPClient{}
	target := CustomTarget(client, "http://192.0.2.42", TLSOptions{}, 0)

	// Not converged initially
	_, err := target.AwaitDeployment(42, 0)
	assert.NotNil(t, err)

	// Not converged on this generation
	response := mock.HTTPResponse{
		URI:    "/application/v2/tenant/default/application/default/environment/prod/region/default/instance/default/serviceconverge",
		Status: 200,
		Body:   []byte(`{"currentGeneration": 42}`),
	}
	client.NextResponse(response)
	_, err = target.AwaitDeployment(41, 0)
	assert.NotNil(t, err)

	// Converged
	client.NextResponse(response)
	convergedID, err := target.AwaitDeployment(42, 0)
	assert.Nil(t, err)
	assert.Equal(t, int64(42), convergedID)
}

func TestCustomTargetCompatibleWith(t *testing.T) {
	client := &mock.HTTPClient{}
	target := CustomTarget(client, "http://192.0.2.42", TLSOptions{}, 0)
	for range 3 {
		client.NextResponse(mock.HTTPResponse{
			URI:    "/state/v1/version",
			Status: 200,
			Body:   []byte(`{"version": "1.2.3"}`),
		})
	}
	assert.Nil(t, target.CompatibleWith(version.MustParse("1.2.2")))
	assert.Nil(t, target.CompatibleWith(version.MustParse("1.2.3")))
	assert.NotNil(t, target.CompatibleWith(version.MustParse("1.2.4")))
	assert.True(t, client.Consumed())
}

func TestCloudTargetWait(t *testing.T) {
	var logWriter bytes.Buffer
	target, client := createCloudTarget(t, &logWriter)
	client.NextResponseError(errAuth)
	assertService(t, true, target, "deploy", time.Second) // No retrying on auth error
	client.NextStatus(401)
	assertService(t, true, target, "deploy", time.Second) // No retrying on 4xx
	client.NextStatus(500)
	client.NextStatus(500)
	client.NextResponse(mock.HTTPResponse{URI: "/status.html", Status: 200})
	assertService(t, false, target, "deploy", time.Second)

	client.NextResponse(mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1",
		Status: 200,
		Body:   []byte(`{"endpoints":[]}`),
	})
	_, err := target.ContainerServices(time.Millisecond)
	assert.NotNil(t, err)

	response := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1",
		Status: 200,
		Body: []byte(`{
  "endpoints": [
    {"url": "http://a.example.com","scope": "zone", "cluster": "default"},
    {"url": "http://b.example.com","scope": "zone", "cluster": "feed"}
  ]
}`),
	}
	client.NextResponse(response)
	services, err := target.ContainerServices(time.Millisecond)
	assert.Nil(t, err)
	assert.Equal(t, 2, len(services))

	client.NextResponse(response)
	client.NextResponse(mock.HTTPResponse{URI: "/status.html", Status: 500})
	assertService(t, true, target, "default", 0)
	client.NextResponse(response)
	client.NextResponse(mock.HTTPResponse{URI: "/status.html", Status: 200})
	assertService(t, false, target, "feed", 0)
}

func TestCloudTargetAwaitDeployment(t *testing.T) {
	var logWriter bytes.Buffer
	target, client := createCloudTarget(t, &logWriter)

	runningResponse := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/run/42?after=-1",
		Status: 200,
		Body: []byte(`{"active": true, "status": "running",
                       "lastId": 42,
                       "log": {"deployReal": [{"at": 1631707708431,
                                               "type": "info",
                                                "message": "Deploying platform version 7.465.17 and application version 1.0.2 ..."}]}}`),
	}
	client.NextResponse(runningResponse)
	runningResponse.URI = "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/run/42?after=42"
	client.NextResponse(runningResponse)
	// Deployment has not succeeded yet
	_, err := target.AwaitDeployment(int64(42), time.Second)
	assert.NotNil(t, err)

	// Log timestamp is converted to local time, do the same here in case the local time where tests are run varies
	tm := time.Unix(1631707708, 431000)
	expectedTime := tm.Format("[15:04:05]")
	assert.Equal(t, strings.Repeat(expectedTime+" info    Deploying platform version 7.465.17 and application version 1.0.2 ...\n", 2), logWriter.String())

	// Wanted deployment run eventually succeeds
	runningResponse.URI = "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/run/42?after=-1"
	client.NextResponse(runningResponse)
	client.NextResponse(mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/run/42?after=42",
		Status: 200,
		Body:   []byte(`{"active": false, "status": "success"}`),
	})
	convergedID, err := target.AwaitDeployment(int64(42), time.Second)
	assert.Nil(t, err)
	assert.Equal(t, int64(42), convergedID)

	// Await latest deployment
	client.NextResponse(mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1?limit=1",
		Status: 200,
		Body:   []byte(`{"runs": [{"id": 1337}]}`),
	})
	client.NextResponse(mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/job/dev-us-north-1/run/1337?after=-1",
		Status: 200,
		Body:   []byte(`{"active": false, "status": "success"}`),
	})
	convergedID, err = target.AwaitDeployment(LatestDeployment, time.Second)
	assert.Nil(t, err)
	assert.Equal(t, int64(1337), convergedID)
}

func TestLog(t *testing.T) {
	target, client := createCloudTarget(t, io.Discard)
	client.NextResponse(mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1/logs?from=-62135596800000",
		Status: 200,
		Body: []byte(`1632738690.905535	host1a.dev.aws-us-east-1c	806/53	logserver-container	Container.com.yahoo.container.jdisc.ConfiguredApplication	info	Switching to the latest deployed set of configurations and components. Application config generation: 52532
1632738698.600189	host1a.dev.aws-us-east-1c	1723/33590	config-sentinel	sentinel.sentinel.config-owner	config	Sentinel got 3 service elements [tenant(vespa-team), application(music), instance(mpolden)] for config generation 52532
`),
	})
	var buf bytes.Buffer
	if err := target.PrintLog(LogOptions{Writer: &buf, Level: 3}); err != nil {
		t.Fatal(err)
	}
	expected := "[2021-09-27 10:31:30.905535] host1a.dev.aws-us-east-1c info    logserver-container Container.com.yahoo.container.jdisc.ConfiguredApplication\tSwitching to the latest deployed set of configurations and components. Application config generation: 52532\n" +
		"[2021-09-27 10:31:38.600189] host1a.dev.aws-us-east-1c config  config-sentinel  sentinel.sentinel.config-owner\tSentinel got 3 service elements [tenant(vespa-team), application(music), instance(mpolden)] for config generation 52532\n"
	assert.Equal(t, expected, buf.String())
}

func TestCloudCompatibleWith(t *testing.T) {
	target, client := createCloudTarget(t, io.Discard)
	for range 3 {
		client.NextResponse(mock.HTTPResponse{URI: "/cli/v1/", Status: 200, Body: []byte(`{"minVersion":"8.0.0"}`)})
	}
	assert.Nil(t, target.CompatibleWith(version.MustParse("8.0.0")))
	assert.Nil(t, target.CompatibleWith(version.MustParse("8.1.0")))
	assert.NotNil(t, target.CompatibleWith(version.MustParse("7.0.0")))
}

func TestCloudTargetWithPrivateServices(t *testing.T) {
	var logWriter bytes.Buffer
	target, client := createCloudTarget(t, &logWriter)

	// Mock endpoints response
	endpointsResponse := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1",
		Status: 200,
		Body: []byte(`{
			"endpoints": [
				{"url": "http://a.example.com","scope": "zone", "cluster": "default", "authMethod": "mtls"},
				{"url": "http://b.example.com","scope": "zone", "cluster": "feed", "authMethod": "mtls"}
			]
		}`),
	}

	// Mock private services response with configured private service
	privateServicesResponse := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1/private-services",
		Status: 200,
		Body: []byte(`{
			"privateServices": [
				{
					"cluster": "default",
					"serviceId": "com.amazonaws.vpce.us-east-1.vpce-svc-1a2b3c4d5e6f7g8h9",
					"type": "aws-private-link",
					"allowedUrns": [
						{
							"type": "aws-private-link",
							"urn": "arn:aws:iam::123456789012:root"
						}
					],
					"authMethods": ["mtls"],
					"endpoints": []
				},
				{
					"cluster": "feed"
				}
			]
		}`),
	}

	client.NextResponse(endpointsResponse)
	client.NextResponse(privateServicesResponse)
	services, err := target.ContainerServices(time.Millisecond)
	assert.Nil(t, err)
	assert.Equal(t, 2, len(services))

	// Verify the default cluster has private service info
	var defaultService *Service
	for _, s := range services {
		if s.Name == "default" && s.AuthMethod == "mtls" {
			defaultService = s
			break
		}
	}
	assert.NotNil(t, defaultService, "Should find default cluster with mtls")
	assert.NotNil(t, defaultService.PrivateService, "Default cluster should have private service info")
	assert.Equal(t, "com.amazonaws.vpce.us-east-1.vpce-svc-1a2b3c4d5e6f7g8h9", defaultService.PrivateService.ServiceID)
	assert.Equal(t, "aws-private-link", defaultService.PrivateService.Type)
	assert.Equal(t, 1, len(defaultService.PrivateService.AllowedUrns))
	assert.Equal(t, "aws-private-link", defaultService.PrivateService.AllowedUrns[0].Type)
	assert.Equal(t, "arn:aws:iam::123456789012:root", defaultService.PrivateService.AllowedUrns[0].Urn)
	assert.Equal(t, []string{"mtls"}, defaultService.PrivateService.AuthMethods)

	// Verify the feed cluster does not have private service info (only cluster name in response)
	var feedService *Service
	for _, s := range services {
		if s.Name == "feed" && s.AuthMethod == "mtls" {
			feedService = s
			break
		}
	}
	assert.NotNil(t, feedService, "Should find feed cluster with mtls")
	assert.Nil(t, feedService.PrivateService, "Feed cluster should not have private service info")
}

func TestCloudTargetWithoutPrivateServices(t *testing.T) {
	var logWriter bytes.Buffer
	target, client := createCloudTarget(t, &logWriter)

	// Mock endpoints response
	endpointsResponse := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1",
		Status: 200,
		Body: []byte(`{
			"endpoints": [
				{"url": "http://a.example.com","scope": "zone", "cluster": "default", "authMethod": "mtls"}
			]
		}`),
	}

	// Mock private services response with no configured private services
	privateServicesResponse := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1/private-services",
		Status: 200,
		Body: []byte(`{
			"privateServices": [
				{
					"cluster": "default"
				}
			]
		}`),
	}

	client.NextResponse(endpointsResponse)
	client.NextResponse(privateServicesResponse)
	services, err := target.ContainerServices(time.Millisecond)
	assert.Nil(t, err)
	assert.Equal(t, 1, len(services))

	// Verify the service does not have private service info
	assert.Equal(t, "default", services[0].Name)
	assert.Nil(t, services[0].PrivateService, "Should not have private service info when only cluster name is returned")
}

func TestCloudTargetPrivateServicesError(t *testing.T) {
	var logWriter bytes.Buffer
	target, client := createCloudTarget(t, &logWriter)

	// Mock endpoints response
	endpointsResponse := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1",
		Status: 200,
		Body: []byte(`{
			"endpoints": [
				{"url": "http://a.example.com","scope": "zone", "cluster": "default", "authMethod": "mtls"}
			]
		}`),
	}

	// Mock private services endpoint returning error (should be ignored)
	privateServicesError := mock.HTTPResponse{
		URI:    "/application/v4/tenant/t1/application/a1/instance/i1/environment/dev/region/us-north-1/private-services",
		Status: 404,
	}

	client.NextResponse(endpointsResponse)
	client.NextResponse(privateServicesError)
	services, err := target.ContainerServices(time.Millisecond)
	// Should still succeed even if private-services endpoint fails
	assert.Nil(t, err)
	assert.Equal(t, 1, len(services))
	assert.Nil(t, services[0].PrivateService, "Should not have private service info when endpoint fails")
}

func createCloudTarget(t *testing.T, logWriter io.Writer) (Target, *mock.HTTPClient) {
	apiKey, err := CreateAPIKey()
	require.Nil(t, err)
	auth := &mockAuthenticator{}
	client := &mock.HTTPClient{}
	target, err := CloudTarget(
		client,
		auth,
		auth,
		APIOptions{APIKey: apiKey, System: PublicSystem},
		CloudDeploymentOptions{
			Deployment: Deployment{
				Application: ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"},
				Zone:        ZoneID{Environment: "dev", Region: "us-north-1"},
				System:      PublicSystem,
			},
		},
		LogOptions{Writer: logWriter},
		0,
	)
	require.Nil(t, err)
	return target, client
}

func getService(t *testing.T, target Target, name string) (*Service, error) {
	t.Helper()
	if name == "deploy" {
		return target.DeployService()
	}
	services, err := target.ContainerServices(0)
	require.Nil(t, err)
	return FindService(name, "mtls", services)
}

func assertServiceURL(t *testing.T, url string, target Target, serviceName string) {
	t.Helper()
	service, err := getService(t, target, serviceName)
	require.Nil(t, err)
	assert.Equal(t, url, service.BaseURL)
}

func assertService(t *testing.T, fail bool, target Target, serviceName string, timeout time.Duration) {
	t.Helper()
	service, err := getService(t, target, serviceName)
	require.Nil(t, err)
	err = service.Wait(timeout)
	if fail {
		assert.NotNil(t, err)
	} else {
		assert.Nil(t, err)
	}
}

type mockAuthenticator struct{}

func (a *mockAuthenticator) Authenticate(request *http.Request) error { return nil }

func TestServiceDescription(t *testing.T) {
	// Container with name
	s := &Service{Name: "foo", deployAPI: false}
	assert.Equal(t, "container", s.Type())
	assert.Equal(t, "foo", s.ServiceName())
	assert.Equal(t, "container foo", s.Description())

	// Container without name
	s = &Service{Name: "", deployAPI: false}
	assert.Equal(t, "container", s.Type())
	assert.Equal(t, "", s.ServiceName())
	assert.Equal(t, "container", s.Description())

	// Deploy API
	s = &Service{Name: "", deployAPI: true}
	assert.Equal(t, "deploy API", s.Type())
	assert.Equal(t, "", s.ServiceName())
	assert.Equal(t, "deploy API", s.Description())

	// Deploy API with name (shouldn't happen in practice, but test the logic)
	s = &Service{Name: "ignored", deployAPI: true}
	assert.Equal(t, "deploy API", s.Type())
	assert.Equal(t, "ignored", s.ServiceName())
	assert.Equal(t, "deploy API ignored", s.Description())
}
