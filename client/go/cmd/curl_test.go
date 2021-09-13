package cmd

import (
	"fmt"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCurl(t *testing.T) {
	homeDir := t.TempDir()
	httpClient := &mockHttpClient{}
	convergeServices(httpClient)
	out := execute(command{homeDir: homeDir, args: []string{"curl", "-n", "-p", "/usr/bin/curl", "-a", "t1.a1.i1", "--", "-v", "--data-urlencode", "arg=with space", "/search"}}, t, httpClient)

	expected := fmt.Sprintf("/usr/bin/curl -v --data-urlencode 'arg=with space' --key %s --cert %s https://127.0.0.1:8080/search\n",
		filepath.Join(homeDir, ".vespa", "t1.a1.i1", "data-plane-private-key.pem"),
		filepath.Join(homeDir, ".vespa", "t1.a1.i1", "data-plane-public-cert.pem"))
	assert.Equal(t, expected, out)
}

func TestCurlCommand(t *testing.T) {
	c := &curl{path: "/usr/bin/curl", privateKeyPath: "/tmp/priv-key", certificatePath: "/tmp/cert-key"}
	assertCurl(t, c, "/usr/bin/curl -v --key /tmp/priv-key --cert /tmp/cert-key https://example.com/", "-v", "/")

	c = &curl{path: "/usr/bin/curl", privateKeyPath: "/tmp/priv-key", certificatePath: "/tmp/cert-key"}
	assertCurl(t, c, "/usr/bin/curl -v --cert my-cert --key my-key https://example.com/", "-v", "--cert", "my-cert", "--key", "my-key", "/")

	c = &curl{path: "/usr/bin/curl2"}
	assertCurl(t, c, "/usr/bin/curl2 -v https://example.com/foo", "-v", "/foo")

	c = &curl{path: "/usr/bin/curl"}
	assertCurl(t, c, "/usr/bin/curl -v https://example.com/foo/bar", "-v", "/foo/bar")

	c = &curl{path: "/usr/bin/curl"}
	assertCurl(t, c, "/usr/bin/curl -v https://example.com/foo/bar", "-v", "foo/bar")

	c = &curl{path: "/usr/bin/curl"}
	assertCurlURL(t, c, "/usr/bin/curl -v https://example.com/foo/bar", "https://example.com/", "-v", "foo/bar")
}

func assertCurl(t *testing.T, c *curl, expectedOutput string, args ...string) {
	assertCurlURL(t, c, expectedOutput, "https://example.com", args...)
}

func assertCurlURL(t *testing.T, c *curl, expectedOutput string, url string, args ...string) {
	cmd, err := c.command("https://example.com", args...)
	assert.Nil(t, err)

	assert.Equal(t, expectedOutput, strings.Join(cmd.Args, " "))
}
