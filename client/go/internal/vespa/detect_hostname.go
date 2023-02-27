// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package vespa

import (
	"fmt"
	"net"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
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
	name, err = findOurHostnameFrom(name)
	if err == nil {
		os.Setenv(envvars.VESPA_HOSTNAME, name)
	}
	return name, err
}

func validateHostname(name string) bool {
	ipAddrs, _ := net.LookupIP(name)
	trace.Debug("lookupIP", name, "=>", ipAddrs)
	if len(ipAddrs) < 1 {
		return false
	}
	myIpAddresses := make(map[string]bool)
	interfaceAddrs, _ := net.InterfaceAddrs()
	for _, ifAddr := range interfaceAddrs {
		// note: ifAddr.String() is typically "127.0.0.1/8"
		if ipnet, ok := ifAddr.(*net.IPNet); ok {
			myIpAddresses[ipnet.IP.String()] = true
		}
	}
	trace.Debug("validate with interfaces =>", myIpAddresses)
	someGood := false
	for _, addr := range ipAddrs {
		if len(myIpAddresses) == 0 {
			// no validation possible, assume OK
			return true
		}
		if myIpAddresses[addr.String()] {
			someGood = true
		} else {
			return false
		}
	}
	return someGood
}

func goodHostname(name string) (result string, good bool) {
	result = strings.TrimSpace(name)
	result = strings.TrimSuffix(result, ".")
	if name != result {
		trace.Trace("trimmed hostname", name, "=>", result)
	}
	good = strings.Contains(result, ".") && validateHostname(result)
	trace.Debug("hostname:", result, "good =>", good)
	return
}

func findOurHostnameFrom(name string) (string, error) {
	trimmed, good := goodHostname(name)
	if good {
		return trimmed, nil
	}
	backticks := util.BackTicksIgnoreStderr
	out, err := backticks.Run("vespa-detect-hostname")
	if err != nil {
		out, err = backticks.Run("hostname", "-f")
	}
	if err != nil {
		out, err = backticks.Run("hostname")
	}
	alternate, good := goodHostname(out)
	if err == nil && good {
		return alternate, nil
	}
	if validateHostname(trimmed) {
		return trimmed, nil
	}
	if validateHostname(alternate) {
		return alternate, nil
	}
	return "localhost", fmt.Errorf("fallback to localhost [os.Hostname was '%s']", name)
}
