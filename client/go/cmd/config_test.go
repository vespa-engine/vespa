// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestConfig(t *testing.T) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	assertConfigCommandErr(t, "invalid option or value: \"foo\": \"bar\"\n", homeDir, "config", "set", "foo", "bar")
	assertConfigCommand(t, "foo = <unset>\n", homeDir, "config", "get", "foo")
	assertConfigCommand(t, "target = local\n", homeDir, "config", "get", "target")
	assertConfigCommand(t, "", homeDir, "config", "set", "target", "cloud")
	assertConfigCommand(t, "target = cloud\n", homeDir, "config", "get", "target")
	assertConfigCommand(t, "", homeDir, "config", "set", "target", "http://127.0.0.1:8080")
	assertConfigCommand(t, "", homeDir, "config", "set", "target", "https://127.0.0.1")
	assertConfigCommand(t, "target = https://127.0.0.1\n", homeDir, "config", "get", "target")

	assertConfigCommandErr(t, "invalid application: \"foo\"\n", homeDir, "config", "set", "application", "foo")
	assertConfigCommand(t, "application = <unset>\n", homeDir, "config", "get", "application")
	assertConfigCommand(t, "", homeDir, "config", "set", "application", "t1.a1.i1")
	assertConfigCommand(t, "application = t1.a1.i1\n", homeDir, "config", "get", "application")

	assertConfigCommand(t, "application = t1.a1.i1\ncolor = auto\nquiet = false\ntarget = https://127.0.0.1\nwait = 0\n", homeDir, "config", "get")

	assertConfigCommand(t, "", homeDir, "config", "set", "wait", "60")
	assertConfigCommandErr(t, "wait option must be an integer >= 0, got \"foo\"\n", homeDir, "config", "set", "wait", "foo")
	assertConfigCommand(t, "wait = 60\n", homeDir, "config", "get", "wait")
}

func assertConfigCommand(t *testing.T, expected, homeDir string, args ...string) {
	out, _ := execute(command{homeDir: homeDir, args: args}, t, nil)
	assert.Equal(t, expected, out)
}

func assertConfigCommandErr(t *testing.T, expected, homeDir string, args ...string) {
	_, outErr := execute(command{homeDir: homeDir, args: args}, t, nil)
	assert.Equal(t, expected, outErr)
}
