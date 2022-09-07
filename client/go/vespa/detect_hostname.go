// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package vespa

import (
	"fmt"
	"net"
	"os"
	"strings"
)

type lookupAddrFunc func(addr string) ([]string, error)
type lookupIPFunc func(host string) ([]net.IP, error)

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
func FindOurHostname() (string, error) { return findOurHostname(net.LookupAddr, net.LookupIP) }

func findOurHostname(lookupAddr lookupAddrFunc, lookupIP lookupIPFunc) (string, error) {
	env := os.Getenv("VESPA_HOSTNAME")
	if env != "" {
		// assumes: env var is already validated and OK
		return env, nil
	}
	name, err := os.Hostname()
	if err != nil {
		name, err = findOurHostnameFrom("localhost", lookupAddr, lookupIP)
	} else {
		name, err = findOurHostnameFrom(name, lookupAddr, lookupIP)
	}
	name = strings.TrimSuffix(name, ".")
	os.Setenv("VESPA_HOSTNAME", name)
	return name, err
}

func validateHostname(name string) bool {
	myIpAddresses := make(map[string]bool)
	interfaceAddrs, _ := net.InterfaceAddrs()
	for _, ifAddr := range interfaceAddrs {
		// note: ifAddr.String() is typically "127.0.0.1/8"
		if ipnet, ok := ifAddr.(*net.IPNet); ok {
			myIpAddresses[ipnet.IP.String()] = true
		}
	}
	ipAddrs, _ := net.LookupIP(name)
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

func findOurHostnameFrom(name string, lookupAddr lookupAddrFunc, lookupIP lookupIPFunc) (string, error) {
	if strings.Contains(name, ".") && validateHostname(name) {
		// it's all good
		return name, nil
	}
	possibles := make([]string, 0, 5)
	if name != "" {
		ipAddrs, _ := lookupIP(name)
		for _, addr := range ipAddrs {
			switch {
			case addr.IsLoopback():
				// skip
			case addr.To4() != nil || addr.To16() != nil:
				reverseNames, _ := lookupAddr(addr.String())
				possibles = append(possibles, reverseNames...)
			}
		}
	}
	interfaceAddrs, _ := net.InterfaceAddrs()
	for _, ifAddr := range interfaceAddrs {
		if ipnet, ok := ifAddr.(*net.IPNet); ok {
			ip := ipnet.IP
			if ip == nil || ip.IsLoopback() {
				continue
			}
			reverseNames, _ := lookupAddr(ip.String())
			possibles = append(possibles, reverseNames...)
		}
	}
	// look for valid possible starting with the given name
	for _, poss := range possibles {
		if strings.HasPrefix(poss, name+".") && validateHostname(poss) {
			return poss, nil
		}
	}
	// look for valid possible
	for _, poss := range possibles {
		if strings.Contains(poss, ".") && validateHostname(poss) {
			return poss, nil
		}
	}
	// look for any valid possible
	for _, poss := range possibles {
		if validateHostname(poss) {
			return poss, nil
		}
	}
	return "localhost", fmt.Errorf("fallback to localhost, os.Hostname '%s'", name)
}
