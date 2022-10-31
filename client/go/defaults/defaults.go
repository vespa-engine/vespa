// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package defaults

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	ENV_VESPA_HOME = util.ENV_VESPA_HOME
	ENV_VESPA_HOST = util.ENV_VESPA_HOSTNAME
	ENV_VESPA_USER = util.ENV_VESPA_USER

	ENV_CONFIGSERVERS     = "VESPA_CONFIGSERVERS"
	ENV_ADDR_CONFIGSERVER = "addr_configserver"

	DEFAULT_VESPA_HOME = "/opt/vespa"
	DEFAULT_VESPA_USER = "vespa"
	DEFAULT_VESPA_HOST = "localhost"

	DEFAULT_VESPA_PORT_BASE = 19000
	ENV_VESPA_PORT_BASE     = "VESPA_PORT_BASE"

	CONFIGSERVER_RPC_PORT_OFFSET = 70
	ENV_CONFIGSERVER_RPC_PORT    = "port_configserver_rpc"

	CONFIGPROXY_RPC_PORT_OFFSET = 90
	ENV_CONFIGPROXY_RPC_PORT    = "port_configproxy_rpc"

	DEFAULT_WEB_SERVICE_PORT = 8080
	ENV_WEB_SERVICE_PORT     = "VESPA_WEB_SERVICE_PORT"
)

// Compute the path prefix where Vespa files will live.
// Note: does not end with "/"
func VespaHome() string {
	if env := os.Getenv(ENV_VESPA_HOME); env != "" {
		return env
	}
	return DEFAULT_VESPA_HOME
}

func UnderVespaHome(p string) string {
	if strings.HasPrefix(p, "/") || strings.HasPrefix(p, "./") {
		return p
	}
	return fmt.Sprintf("%s/%s", VespaHome(), p)
}

// Compute the user name to own directories and run processes.
func VespaUser() string {
	if env := os.Getenv(ENV_VESPA_USER); env != "" {
		return env
	}
	return DEFAULT_VESPA_USER
}

// Compute the host name that identifies myself.
// Detection of the hostname is now done before starting any Vespa
// programs and provided in the environment variable VESPA_HOSTNAME;
// if that variable isn't set a default of "localhost" is always returned.
func VespaHostname() string {
	if env := os.Getenv(ENV_VESPA_HOST); env != "" {
		return env
	}
	return DEFAULT_VESPA_HOST
}

// Compute the port number where the Vespa webservice
// container should be available.
func VespaContainerWebServicePort() int {
	p := getNumFromEnv(ENV_WEB_SERVICE_PORT)
	if p > 0 {
		trace.Debug(ENV_WEB_SERVICE_PORT, p)
		return p
	}
	return DEFAULT_WEB_SERVICE_PORT
}

// Compute the base for port numbers where the Vespa services should listen.
func VespaPortBase() int {
	p := getNumFromEnv(ENV_VESPA_PORT_BASE)
	if p > 0 {
		trace.Debug(ENV_VESPA_PORT_BASE, p)
		return p
	}
	return DEFAULT_VESPA_PORT_BASE
}

// Find the hostnames of configservers that are configured.
func VespaConfigserverHosts() []string {
	parts := splitVespaConfigservers()
	rv := make([]string, len(parts))
	for idx, part := range parts {
		if colon := strings.Index(part, ":"); colon > 0 {
			rv[idx] = part[0:colon]
		} else {
			rv[idx] = part
		}
		trace.Debug("config server host:", rv[idx])
	}
	return rv
}

func findConfigserverHttpPort() int {
	return findConfigserverRpcPort() + 1
}

// Find the RPC addresses to configservers that are configured.
// Returns a list of RPC specs in the format tcp/{hostname}:{portnumber}
func VespaConfigserverRpcAddrs() []string {
	parts := splitVespaConfigservers()
	rv := make([]string, len(parts))
	for idx, part := range parts {
		if colon := strings.Index(part, ":"); colon > 0 {
			rv[idx] = fmt.Sprintf("tcp/%s", part)
		} else {
			rv[idx] = fmt.Sprintf("tcp/%s:%d", part, findConfigserverRpcPort())
		}
		trace.Debug("config server rpc addr:", rv[idx])
	}
	return rv
}

// Find the URLs to the REST api on configservers
// Returns a list of URLS in the format http://{hostname}:{portnumber}/
func VespaConfigserverRestUrls() []string {
	parts := splitVespaConfigservers()
	rv := make([]string, len(parts))
	for idx, hostnm := range parts {
		port := findConfigserverHttpPort()
		if colon := strings.Index(hostnm, ":"); colon > 0 {
			p, err := strconv.Atoi(hostnm[colon+1:])
			if err == nil && p > 0 {
				port = p + 1
			}
			hostnm = hostnm[:colon]
		}
		rv[idx] = fmt.Sprintf("http://%s:%d", hostnm, port)
		trace.Debug("config server rest url:", rv[idx])
	}
	return rv
}

// Find the RPC address to the local config proxy
// Returns one RPC spec in the format tcp/{hostname}:{portnumber}
func VespaConfigProxyRpcAddr() string {
	return fmt.Sprintf("tcp/localhost:%d", findConfigproxyRpcPort())
}

// Get the RPC addresses to all known config sources
// Returns same as vespaConfigProxyRpcAddr + vespaConfigserverRpcAddrs
func VespaConfigSourcesRpcAddrs() []string {
	cs := VespaConfigserverRpcAddrs()
	rv := make([]string, 0, len(cs)+1)
	rv = append(rv, VespaConfigProxyRpcAddr())
	for _, addr := range cs {
		rv = append(rv, addr)
	}
	return rv
}

func splitVespaConfigservers() []string {
	env := os.Getenv(ENV_CONFIGSERVERS)
	if env == "" {
		env = os.Getenv(ENV_ADDR_CONFIGSERVER)
	}
	parts := make([]string, 0, 3)
	for {
		idx := strings.IndexAny(env, " ,")
		if idx < 0 {
			break
		}
		if idx > 0 {
			parts = append(parts, env[:idx])
		}
		env = env[idx+1:]
	}
	if env != "" {
		parts = append(parts, env)
	}
	if len(parts) == 0 {
		parts = append(parts, "localhost")
	}
	return parts
}

func findConfigproxyRpcPort() int {
	p := getNumFromEnv(ENV_CONFIGPROXY_RPC_PORT)
	if p > 0 {
		return p
	}
	return VespaPortBase() + CONFIGPROXY_RPC_PORT_OFFSET
}

func findConfigserverRpcPort() int {
	p := getNumFromEnv(ENV_CONFIGSERVER_RPC_PORT)
	if p > 0 {
		trace.Debug(ENV_CONFIGSERVER_RPC_PORT, p)
		return p
	}
	return VespaPortBase() + CONFIGSERVER_RPC_PORT_OFFSET
}

func getNumFromEnv(vn string) int {
	env := os.Getenv(vn)
	if env != "" {
		p, err := strconv.Atoi(env)
		if err == nil {
			return p
		}
		trace.Debug("env var", vn, "is:", env, "parse error:", err)
	}
	return -1
}
