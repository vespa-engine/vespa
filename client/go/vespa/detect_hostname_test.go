// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"fmt"
	"net"
	"os"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDetectHostname(t *testing.T) {
	lookupAddr := func(addr string) ([]string, error) {
		return nil, fmt.Errorf("could not look up %s", addr)
	}
	lookupIP := func(host string) ([]net.IP, error) {
		return nil, fmt.Errorf("no address found for %s", host)
	}

	t.Setenv("VESPA_HOSTNAME", "foo.bar")
	got, err := findOurHostname(lookupAddr, lookupIP)
	assert.Nil(t, err)
	assert.Equal(t, "foo.bar", got)
	os.Unsetenv("VESPA_HOSTNAME")
	got, err = findOurHostnameFrom("bar.foo.123", lookupAddr, lookupIP)
	fmt.Fprintln(os.Stderr, "findOurHostname from bar.foo.123 returns:", got, "with error:", err)
	assert.NotEqual(t, "", got)
	parts := strings.Split(got, ".")
	if len(parts) > 1 {
		expanded, err2 := findOurHostnameFrom(parts[0], lookupAddr, lookupIP)
		fmt.Fprintln(os.Stderr, "findOurHostname from", parts[0], "returns:", expanded, "with error:", err2)
		assert.Equal(t, got, expanded)
	}
	got, err = findOurHostname(lookupAddr, lookupIP)
	assert.NotEqual(t, "", got)
	fmt.Fprintln(os.Stderr, "findOurHostname() returns:", got, "with error:", err)
}
