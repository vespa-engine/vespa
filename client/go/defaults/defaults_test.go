// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package defaults

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/trace"
)

func setup(t *testing.T) {
	t.Setenv("VESPA_HOME", "/home/v/1")
	t.Setenv("VESPA_USER", "somebody")
	t.Setenv("VESPA_HOSTNAME", "foo.bar.local")
	t.Setenv("VESPA_CONFIGSERVERS", "foo1.local, bar2.local, baz3.local")
	t.Setenv("VESPA_PORT_BASE", "17000")
	t.Setenv("port_configserver_rpc", "")
	t.Setenv("port_configproxy_rpc", "")
	t.Setenv("VESPA_WEB_SERVICE_PORT", "")
}

func TestWebServicePort(t *testing.T) {
	trace.AdjustVerbosity(1)
	setup(t)
	ws := VespaContainerWebServicePort()
	assert.Equal(t, 8080, ws)

	t.Setenv("VESPA_WEB_SERVICE_PORT", "4488")
	ws = VespaContainerWebServicePort()
	assert.Equal(t, 4488, ws)
}

func TestConfigProxyRpcAddr(t *testing.T) {
	setup(t)
	addr := VespaConfigProxyRpcAddr()
	assert.Equal(t, "tcp/localhost:17090", addr)
	t.Setenv("VESPA_PORT_BASE", "")
	addr = VespaConfigProxyRpcAddr()
	assert.Equal(t, "tcp/localhost:19090", addr)
	t.Setenv("port_configproxy_rpc", "16066")
	addr = VespaConfigProxyRpcAddr()
	assert.Equal(t, "tcp/localhost:16066", addr)
}

func TestConfigSourcesRpcAddrs(t *testing.T) {
	setup(t)
	cs := VespaConfigSourcesRpcAddrs()
	assert.Equal(t, len(cs), 4)
	assert.Equal(t, cs[0], "tcp/localhost:17090")
	assert.Equal(t, cs[1], "tcp/foo1.local:17070")
	t.Setenv("port_configserver_rpc", "12345")
	cs = VespaConfigSourcesRpcAddrs()
	assert.Equal(t, len(cs), 4)
	assert.Equal(t, cs[1], "tcp/foo1.local:12345")
}

func TestConfigserverHosts(t *testing.T) {
	setup(t)
	var cs []string
	t.Setenv("VESPA_CONFIGSERVERS", "foo.bar")
	cs = VespaConfigserverHosts()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "foo.bar")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "tcp/foo.bar:17070")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "http://foo.bar:17071")

	t.Setenv("VESPA_CONFIGSERVERS", "foo.bar:18080")
	cs = VespaConfigserverHosts()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "foo.bar")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "tcp/foo.bar:18080")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "http://foo.bar:18081")

	t.Setenv("VESPA_CONFIGSERVERS", "foo.local, bar.local")
	cs = VespaConfigserverHosts()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "foo.local")
	assert.Equal(t, cs[1], "bar.local")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "tcp/foo.local:17070")
	assert.Equal(t, cs[1], "tcp/bar.local:17070")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "http://foo.local:17071")
	assert.Equal(t, cs[1], "http://bar.local:17071")

	t.Setenv("VESPA_CONFIGSERVERS", "foo bar")
	cs = VespaConfigserverHosts()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "foo")
	assert.Equal(t, cs[1], "bar")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "tcp/foo:17070")
	assert.Equal(t, cs[1], "tcp/bar:17070")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "http://foo:17071")
	assert.Equal(t, cs[1], "http://bar:17071")

	t.Setenv("VESPA_CONFIGSERVERS", "foo,bar")
	cs = VespaConfigserverHosts()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "foo")
	assert.Equal(t, cs[1], "bar")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "tcp/foo:17070")
	assert.Equal(t, cs[1], "tcp/bar:17070")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "http://foo:17071")
	assert.Equal(t, cs[1], "http://bar:17071")

	t.Setenv("VESPA_CONFIGSERVERS", " foo , bar, ")
	cs = VespaConfigserverHosts()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "foo")
	assert.Equal(t, cs[1], "bar")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "tcp/foo:17070")
	assert.Equal(t, cs[1], "tcp/bar:17070")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 2)
	assert.Equal(t, cs[0], "http://foo:17071")
	assert.Equal(t, cs[1], "http://bar:17071")

	os.Unsetenv("VESPA_CONFIGSERVERS")
	cs = VespaConfigserverHosts()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "localhost")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "tcp/localhost:17070")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "http://localhost:17071")

	t.Setenv("VESPA_PORT_BASE", "")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "tcp/localhost:19070")
}

func TestConfigserverHostsWithPortOverride(t *testing.T) {
	setup(t)
	var cs []string
	t.Setenv("VESPA_CONFIGSERVERS", "foo.bar")
	t.Setenv("port_configserver_rpc", "12345")
	cs = VespaConfigserverRpcAddrs()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "tcp/foo.bar:12345")
	cs = VespaConfigserverRestUrls()
	assert.Equal(t, len(cs), 1)
	assert.Equal(t, cs[0], "http://foo.bar:12346")
}

func TestUser(t *testing.T) {
	setup(t)
	user := VespaUser()
	assert.Equal(t, "somebody", user)
	t.Setenv("VESPA_USER", "")
	user = VespaUser()
	assert.Equal(t, "vespa", user)
}

func TestHome(t *testing.T) {
	setup(t)
	home := VespaHome()
	assert.Equal(t, "/home/v/1", home)
	t.Setenv("VESPA_HOME", "")
	home = VespaHome()
	assert.Equal(t, "/opt/vespa", home)
}

func TestHost(t *testing.T) {
	setup(t)
	host := VespaHostname()
	assert.Equal(t, "foo.bar.local", host)
	t.Setenv("VESPA_HOSTNAME", "")
	host = VespaHostname()
	assert.Equal(t, "localhost", host)
}
