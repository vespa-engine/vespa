// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package vespa

import (
	"net"
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
)

// detect if this host is IPv6-only, in which case we want to pass
// the flag "-Djava.net.preferIPv6Addresses=true" to any java command
func HasOnlyIpV6() bool {
	hostname, err := FindOurHostname()
	if hostname == "" || err != nil {
		return false
	}
	foundV4 := false
	foundV6 := false
	ipAddrs, err := net.LookupIP(hostname)
	if err != nil {
		return false
	}
	for _, addr := range ipAddrs {
		switch {
		case addr.IsLoopback():
			// skip
		case addr.To4() != nil:
			foundV4 = true
		case addr.To16() != nil:
			foundV6 = true
		}
	}
	return foundV6 && !foundV4
}

// Find a good name for the host we're running on.
// We need something that *other* hosts can use for connnecting back
// to our services, preferably the canonical DNS name.
// If automatic detection fails, "localhost" will be returned, so
// single-node setups still have a good chance of working.
// Use the enviroment variable VESPA_HOSTNAME to override.
func FindOurHostname() (string, error) {
	env := os.Getenv(envvars.VESPA_HOSTNAME)
	if env != "" {
		// assumes: env var is already validated and OK
		return env, nil
	}
	name, err := os.Hostname()
	if err != nil {
		name = "localhost"
	}
	if err == nil {
		os.Setenv(envvars.VESPA_HOSTNAME, name)
	}
	return name, err
}
