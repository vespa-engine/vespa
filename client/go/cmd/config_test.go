// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestConfig(t *testing.T) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	assertConfigCommandErr(t, "Error: invalid option or value: \"foo\": \"bar\"\n", homeDir, "config", "set", "foo", "bar")
	assertConfigCommand(t, "foo = <unset>\n", homeDir, "config", "get", "foo")
	assertConfigCommand(t, "target = local\n", homeDir, "config", "get", "target")
	assertConfigCommand(t, "", homeDir, "config", "set", "target", "cloud")
	assertConfigCommand(t, "target = cloud\n", homeDir, "config", "get", "target")
	assertConfigCommand(t, "", homeDir, "config", "set", "target", "http://127.0.0.1:8080")
	assertConfigCommand(t, "", homeDir, "config", "set", "target", "https://127.0.0.1")
	assertConfigCommand(t, "target = https://127.0.0.1\n", homeDir, "config", "get", "target")
	assertEnvConfigCommand(t, "api-key-file = /tmp/private.key\n", homeDir, map[string]string{"VESPA_CLI_API_KEY_FILE": "/tmp/private.key"}, "config", "get", "api-key-file")
	assertConfigCommand(t, "", homeDir, "config", "set", "api-key-file", "/tmp/private.key")
	assertConfigCommand(t, "api-key-file = /tmp/private.key\n", homeDir, "config", "get", "api-key-file")

	assertConfigCommandErr(t, "Error: invalid application: \"foo\"\n", homeDir, "config", "set", "application", "foo")
	assertConfigCommand(t, "application = <unset>\n", homeDir, "config", "get", "application")
	assertConfigCommand(t, "", homeDir, "config", "set", "application", "t1.a1.i1")
	assertConfigCommand(t, "application = t1.a1.i1\n", homeDir, "config", "get", "application")

	assertConfigCommand(t, "api-key-file = /tmp/private.key\napplication = t1.a1.i1\ncolor = auto\nquiet = false\ntarget = https://127.0.0.1\nwait = 0\n", homeDir, "config", "get")

	assertConfigCommand(t, "", homeDir, "config", "set", "wait", "60")
	assertConfigCommandErr(t, "Error: wait option must be an integer >= 0, got \"foo\"\n", homeDir, "config", "set", "wait", "foo")
	assertConfigCommand(t, "wait = 60\n", homeDir, "config", "get", "wait")
}

func assertConfigCommand(t *testing.T, expected, homeDir string, args ...string) {
	assertEnvConfigCommand(t, expected, homeDir, nil, args...)
}

func assertEnvConfigCommand(t *testing.T, expected, homeDir string, env map[string]string, args ...string) {
	out, _ := execute(command{homeDir: homeDir, env: env, args: args}, t, nil)
	assert.Equal(t, expected, out)
}

func assertConfigCommandErr(t *testing.T, expected, homeDir string, args ...string) {
	_, outErr := execute(command{homeDir: homeDir, args: args}, t, nil)
	assert.Equal(t, expected, outErr)
}

func withEnv(key, value string, fn func()) {
	orig, ok := os.LookupEnv(key)
	os.Setenv(key, value)
	fn()
	if ok {
		os.Setenv(key, orig)
	} else {
		os.Unsetenv(key)
	}
}

func TestUseAPIKey(t *testing.T) {
	homeDir := t.TempDir()
	c := Config{Home: homeDir}

	assert.False(t, c.UseAPIKey("t1"))

	c.Set(apiKeyFileFlag, "/tmp/foo")
	assert.True(t, c.UseAPIKey("t1"))
	c.Set(apiKeyFileFlag, "")

	withEnv("VESPA_CLI_API_KEY", "...", func() {
		require.Nil(t, c.load())
		assert.True(t, c.UseAPIKey("t1"))
	})

	// Test deprecated functionality
	authContent := `
{
    "version": 1,
    "providers": {
        "auth0": {
            "version": 1,
            "systems": {
                "public": {
					"access_token": "...",
					"scopes": ["openid", "offline_access"],
					"expires_at": "2030-01-01T01:01:01.000001+01:00"
				}
			}
		}
	}
}`
	withEnv("VESPA_CLI_CLOUD_SYSTEM", "public", func() {
		_, err := os.Create(filepath.Join(homeDir, "t2.api-key.pem"))
		require.Nil(t, err)
		assert.True(t, c.UseAPIKey("t2"))
		require.Nil(t, ioutil.WriteFile(filepath.Join(homeDir, "auth.json"), []byte(authContent), 0600))
		assert.False(t, c.UseAPIKey("t2"))
	})
}
