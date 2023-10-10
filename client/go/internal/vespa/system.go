// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import "fmt"

// PublicSystem represents the main Vespa Cloud system.
var PublicSystem = System{
	Name:           "public",
	URL:            "https://api-ctl.vespa-cloud.com:4443",
	ConsoleURL:     "https://console.vespa-cloud.com",
	DefaultZone:    ZoneID{Environment: "dev", Region: "aws-us-east-1c"},
	EndpointDomain: "vespa-app.cloud",
}

// PublicCDSystem represents the CD variant of the Vespa Cloud system.
var PublicCDSystem = System{
	Name:           "publiccd",
	URL:            "https://api-ctl.cd.vespa-cloud.com:4443",
	ConsoleURL:     "https://console.cd.vespa-cloud.com",
	DefaultZone:    ZoneID{Environment: "dev", Region: "aws-us-east-1c"},
	EndpointDomain: "cd.vespa-app.cloud",
}

// MainSystem represents the main hosted Vespa system.
var MainSystem = System{
	Name:           "main",
	URL:            "https://api.vespa.ouryahoo.com:4443",
	ConsoleURL:     "https://console.vespa.ouryahoo.com",
	DefaultZone:    ZoneID{Environment: "dev", Region: "us-east-1"},
	AthenzDomain:   "vespa.vespa",
	EndpointDomain: "vespa.oath.cloud",
}

// CDSystem represents the CD variant of the hosted Vespa system.
var CDSystem = System{
	Name:           "cd",
	URL:            "https://api-cd.vespa.ouryahoo.com:4443",
	ConsoleURL:     "https://console-cd.vespa.ouryahoo.com",
	DefaultZone:    ZoneID{Environment: "dev", Region: "cd-us-west-1"},
	AthenzDomain:   "vespa.vespa.cd",
	EndpointDomain: "cd.vespa.oath.cloud",
}

// System represents a Vespa system.
type System struct {
	Name string
	// URL is the API URL for this system.
	URL        string
	ConsoleURL string
	// DefaultZone is default zone to use in manual deployments to this system.
	DefaultZone ZoneID
	// AthenzDomain is the Athenz domain used by this system. This is empty for systems not using Athenz for tenant
	// authentication.
	AthenzDomain string
	// EndpointDomain is the domain used for application endpoints in this system
	EndpointDomain string
}

// IsPublic returns whether system s is a public (Vespa Cloud) system.
func (s System) IsPublic() bool { return s.Name == PublicSystem.Name || s.Name == PublicCDSystem.Name }

// DeployURL returns the API URL to use for deploying to this system.
func (s System) DeployURL(deployment Deployment) string {
	return fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/deploy/%s",
		s.URL,
		deployment.Application.Tenant,
		deployment.Application.Application,
		deployment.Application.Instance,
		jobName(deployment.Zone))
}

// SubmitURL returns the API URL for submitting an application package for production deployment.
func (s System) SubmitURL(deployment Deployment) string {
	return fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/submit", s.URL, deployment.Application.Tenant, deployment.Application.Application)
}

// DeploymentURL returns the API URL of given deployment.
func (s System) DeploymentURL(deployment Deployment) string {
	return fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/environment/%s/region/%s",
		s.URL,
		deployment.Application.Tenant,
		deployment.Application.Application,
		deployment.Application.Instance,
		deployment.Zone.Environment,
		deployment.Zone.Region)
}

// RunURL returns the API URL for a given deployment job run.
func (s System) RunURL(deployment Deployment, id int64) string {
	return fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/job/%s/run/%d",
		s.URL,
		deployment.Application.Tenant, deployment.Application.Application, deployment.Application.Instance,
		jobName(deployment.Zone), id)
}

// RunsURL returns the API URL listing all runs for given deployment.
func (s System) RunsURL(deployment Deployment) string {
	return fmt.Sprintf("%s/application/v4/tenant/%s/application/%s/instance/%s/job/%s",
		s.URL,
		deployment.Application.Tenant, deployment.Application.Application, deployment.Application.Instance,
		jobName(deployment.Zone))
}

// ConsoleRunURL returns the console URL for a deployment job run in this system.
func (s System) ConsoleRunURL(deployment Deployment, run int64) string {
	return fmt.Sprintf("%s/tenant/%s/application/%s/%s/instance/%s/job/%s/run/%d",
		s.ConsoleURL, deployment.Application.Tenant, deployment.Application.Application, deployment.Zone.Environment,
		deployment.Application.Instance, jobName(deployment.Zone), run)
}

func jobName(zone ZoneID) string {
	env := zone.Environment
	if env == "prod" {
		env = "production"
	}
	return env + "-" + zone.Region
}

// GetSystem returns the system of given name.
func GetSystem(name string) (System, error) {
	switch name {
	case "cd":
		return CDSystem, nil
	case "main":
		return MainSystem, nil
	case "public":
		return PublicSystem, nil
	case "publiccd":
		return PublicCDSystem, nil
	}
	return System{}, fmt.Errorf("invalid system: %s", name)
}
