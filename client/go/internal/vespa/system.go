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
func (s *System) IsPublic() bool { return s.Name == PublicSystem.Name || s.Name == PublicCDSystem.Name }

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
