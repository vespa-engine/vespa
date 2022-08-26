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
	hostname, err := os.Hostname()
	hostname, err = FindOurHostname(hostname)
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

func FindOurHostname(name string) (string, error) {
	env := os.Getenv("VESPA_HOSTNAME")
	if env != "" {
		// assumes: env var is already validated and OK
		fmt.Fprintln(os.Stderr, "from env:", env)
		return env, nil
	}
	ifAddrs, _ := net.InterfaceAddrs()
	fmt.Fprintln(os.Stderr, "interface addrs:", ifAddrs)
	var checkIsMine = func(addr string) bool {
		if len(ifAddrs) == 0 {
			// no validation possible, assume OK
			return true
		}
		for _, ifAddr := range ifAddrs {
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
		fmt.Fprintln(os.Stderr, "LookupIP", name, "->", ipAddrs)
		for _, addr := range ipAddrs {
			switch {
			case addr.IsLoopback():
				// skip
			case addr.To4() != nil || addr.To16() != nil:
				if checkIsMine(addr.String()) {
					reverseNames, _ := net.LookupAddr(addr.String())
					for _, reverse := range reverseNames {
						if strings.HasPrefix(reverse, name) {
							fmt.Fprintln(os.Stderr, "hostname", name, "->", addr, "-> ", reverse)
							return reverse, nil
						}
					}
					if len(reverseNames) > 0 {
						reverse := reverseNames[0]
						fmt.Fprintln(os.Stderr, "hostname", name, "->", addr, "-> ", reverse)
						return reverse, nil
					}
				}
			}
		}
	}
	for _, ifAddr := range ifAddrs {
		if ipnet, ok := ifAddr.(*net.IPNet); ok {
			ip := ipnet.IP
			fmt.Fprintln(os.Stderr, "converted IP", ifAddr, "->", ip)
			if ip == nil || ip.IsLoopback() {
				continue
			}
			reverseNames, _ := net.LookupAddr(ip.String())
			if len(reverseNames) > 0 {
				reverse := reverseNames[0]
				fmt.Fprintln(os.Stderr, "interface", ifAddr, "->", reverse)
				return reverse, nil
			}
		}
	}
	if name != "" {
		return name, fmt.Errorf("unvalidated hostname '%s'", name)
	}
	return "localhost", fmt.Errorf("fallback to localhost, os.Hostname '%s'", name)
}
