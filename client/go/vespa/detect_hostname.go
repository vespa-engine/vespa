// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package vespa

import (
	"fmt"
	"net"
	"os"
	"strings"
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
	env := os.Getenv("VESPA_HOSTNAME")
	if env != "" {
		// assumes: env var is already validated and OK
		return env, nil
	}
	name, err := os.Hostname()
	if err != nil {
		return findOurHostnameFrom("localhost")
	}
	name, err = findOurHostnameFrom(name)
	if strings.HasSuffix(name, ".") {
		name = name[:len(name)-1]
	}
	return name, err
}

func findOurHostnameFrom(name string) (string, error) {
	ifAddrs, _ := net.InterfaceAddrs()
	var checkIsMine = func(addr string) bool {
		if len(ifAddrs) == 0 {
			// no validation possible, assume OK
			return true
		}
		for _, ifAddr := range ifAddrs {
			// note: ifAddr.String() is typically "127.0.0.1/8"
			if ipnet, ok := ifAddr.(*net.IPNet); ok {
				if ipnet.IP.String() == addr {
					return true
				}
			}
		}
		return false
	}
	if name != "" {
		ipAddrs, _ := net.LookupIP(name)
		for _, addr := range ipAddrs {
			switch {
			case addr.IsLoopback():
				// skip
			case addr.To4() != nil || addr.To16() != nil:
				if checkIsMine(addr.String()) {
					reverseNames, _ := net.LookupAddr(addr.String())
					for _, reverse := range reverseNames {
						if strings.HasPrefix(reverse, name) {
							return reverse, nil
						}
					}
					if len(reverseNames) > 0 {
						reverse := reverseNames[0]
						return reverse, nil
					}
				}
			}
		}
	}
	for _, ifAddr := range ifAddrs {
		if ipnet, ok := ifAddr.(*net.IPNet); ok {
			ip := ipnet.IP
			if ip == nil || ip.IsLoopback() {
				continue
			}
			reverseNames, _ := net.LookupAddr(ip.String())
			if len(reverseNames) > 0 {
				reverse := reverseNames[0]
				return reverse, nil
			}
		}
	}
	if name != "" {
		return name, fmt.Errorf("unvalidated hostname '%s'", name)
	}
	return "localhost", fmt.Errorf("fallback to localhost, os.Hostname '%s'", name)
}
