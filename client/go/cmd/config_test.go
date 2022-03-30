// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func TestConfig(t *testing.T) {
	assertConfigCommandErr(t, "Error: invalid option or value: \"foo\": \"bar\"\n", "config", "set", "foo", "bar")
	assertConfigCommand(t, "foo = <unset>\n", "config", "get", "foo")
	assertConfigCommand(t, "target = local\n", "config", "get", "target")
	assertConfigCommand(t, "", "config", "set", "target", "hosted")
	assertConfigCommand(t, "target = hosted\n", "config", "get", "target")
	assertConfigCommand(t, "", "config", "set", "target", "cloud")
	assertConfigCommand(t, "target = cloud\n", "config", "get", "target")
	assertConfigCommand(t, "", "config", "set", "target", "http://127.0.0.1:8080")
	assertConfigCommand(t, "", "config", "set", "target", "https://127.0.0.1")
	assertConfigCommand(t, "target = https://127.0.0.1\n", "config", "get", "target")
	assertEnvConfigCommand(t, "api-key-file = /tmp/private.key\n", []string{"VESPA_CLI_API_KEY_FILE=/tmp/private.key"}, "config", "get", "api-key-file")
	assertConfigCommand(t, "", "config", "set", "api-key-file", "/tmp/private.key")
	assertConfigCommand(t, "api-key-file = /tmp/private.key\n", "config", "get", "api-key-file")

	assertConfigCommandErr(t, "Error: invalid application: \"foo\"\n", "config", "set", "application", "foo")
	assertConfigCommand(t, "application = <unset>\n", "config", "get", "application")
	assertConfigCommand(t, "", "config", "set", "application", "t1.a1.i1")
	assertConfigCommand(t, "application = t1.a1.i1\n", "config", "get", "application")

	assertConfigCommand(t, "api-key-file = /tmp/private.key\napplication = t1.a1.i1\ncolor = auto\ninstance = <unset>\nquiet = false\ntarget = https://127.0.0.1\nwait = 0\nzone = <unset>\n", "config", "get")

	assertConfigCommand(t, "", "config", "set", "wait", "60")
	assertConfigCommandErr(t, "Error: wait option must be an integer >= 0, got \"foo\"\n", "config", "set", "wait", "foo")
	assertConfigCommand(t, "wait = 60\n", "config", "get", "wait")

	assertConfigCommand(t, "", "config", "set", "quiet", "true")
	assertConfigCommand(t, "", "config", "set", "quiet", "false")

	assertConfigCommand(t, "", "config", "set", "instance", "i2")
	assertConfigCommand(t, "instance = i2\n", "config", "get", "instance")

	assertConfigCommand(t, "", "config", "set", "application", "t1.a1")
	assertConfigCommand(t, "application = t1.a1.default\n", "config", "get", "application")
}

func assertConfigCommand(t *testing.T, expected string, args ...string) {
	assertEnvConfigCommand(t, expected, nil, args...)
}

func assertEnvConfigCommand(t *testing.T, expected string, env []string, args ...string) {
	cli, stdout, _ := newTestCLI(t, env...)
	err := cli.Run(args...)
	assert.Nil(t, err)
	assert.Equal(t, expected, stdout.String())
}

func assertConfigCommandErr(t *testing.T, expected string, args ...string) {
	cli, _, stderr := newTestCLI(t)
	err := cli.Run(args...)
	assert.NotNil(t, err)
	assert.Equal(t, expected, stderr.String())
}

func TestUseAPIKey(t *testing.T) {
	cli, _, _ := newTestCLI(t)

	assert.False(t, cli.config.useAPIKey(cli, vespa.PublicSystem, "t1"))

	cli.config.set(apiKeyFileFlag, "/tmp/foo")
	assert.True(t, cli.config.useAPIKey(cli, vespa.PublicSystem, "t1"))
	cli.config.set(apiKeyFileFlag, "")

	cli, _, _ = newTestCLI(t, "VESPA_CLI_API_KEY=foo")
	assert.True(t, cli.config.useAPIKey(cli, vespa.PublicSystem, "t1"))

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
	cli, _, _ = newTestCLI(t, "VESPA_CLI_CLOUD_SYSTEM=public")
	_, err := os.Create(filepath.Join(cli.config.homeDir, "t2.api-key.pem"))
	require.Nil(t, err)
	assert.True(t, cli.config.useAPIKey(cli, vespa.PublicSystem, "t2"))
	require.Nil(t, os.WriteFile(filepath.Join(cli.config.homeDir, "auth.json"), []byte(authContent), 0600))
	assert.False(t, cli.config.useAPIKey(cli, vespa.PublicSystem, "t2"))
}
