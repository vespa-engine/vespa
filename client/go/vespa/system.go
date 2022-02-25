package vespa

import "fmt"

// PublicSystem represents the main Vespa Cloud system.
var PublicSystem = System{
	Name:        "public",
	URL:         "https://api.vespa-external.aws.oath.cloud:4443",
	ConsoleURL:  "https://console.vespa.oath.cloud",
	DefaultZone: ZoneID{Environment: "dev", Region: "aws-us-east-1c"},
}

// PublicCDSystem represents the CD variant of the Vespa Cloud system.
var PublicCDSystem = System{
	Name:        "publiccd",
	URL:         "https://api.vespa-external-cd.aws.oath.cloud:4443",
	ConsoleURL:  "https://console-cd.vespa.oath.cloud",
	DefaultZone: ZoneID{Environment: "dev", Region: "aws-us-east-1c"},
}

// MainSystem represents the main hosted Vespa system.
var MainSystem = System{
	Name:        "main",
	URL:         "https://api.vespa.ouryahoo.com:4443",
	ConsoleURL:  "https://console.vespa.ouryahoo.com",
	DefaultZone: ZoneID{Environment: "dev", Region: "us-east-1"},
}

// CDSystem represents the CD variant of the hosted Vespa system.
var CDSystem = System{
	Name:        "cd",
	URL:         "https://api-cd.vespa.ouryahoo.com:4443",
	ConsoleURL:  "https://console-cd.vespa.ouryahoo.com",
	DefaultZone: ZoneID{Environment: "dev", Region: "cd-us-west-1"},
}

// System represents a Vespa system.
type System struct {
	Name string
	// URL is the API URL for this system.
	URL        string
	ConsoleURL string
	// DefaultZone declares the default zone for manual deployments to this system.
	DefaultZone ZoneID
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
