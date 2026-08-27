// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestMaybePromptSkillsInstallDeclines(t *testing.T) {
	chdirTemp(t)
	cli, stdout, stderr := newTestCLI(t)
	// newTestCLI pre-seeds the marker to suppress this prompt in other tests; remove it to exercise the real
	// first-run path.
	marker := filepath.Join(cli.config.homeDir, skillsPromptedMarker)
	require.Nil(t, os.Remove(marker))
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("n\n")

	require.Nil(t, cli.Run("config", "get"))
	assert.Contains(t, stdout.String(), "Install them for Claude Code, Codex, Antigravity CLI or Cursor?")
	assert.Contains(t, stderr.String(), "You can install these later by running 'vespa skills install', or by running 'npx skills add vespaai-playground/skills'")
	assert.FileExists(t, marker)

	// Running another command again should not prompt a second time.
	stdout.Reset()
	require.Nil(t, cli.Run("config", "get"))
	assert.NotContains(t, stdout.String(), "Install Vespa AI-assistant skills")
}

func TestMaybePromptSkillsInstallSkipsWhenManifestExists(t *testing.T) {
	chdirTemp(t)
	cli, stdout, _ := newTestCLI(t)
	marker := filepath.Join(cli.config.homeDir, skillsPromptedMarker)
	require.Nil(t, os.Remove(marker))
	cli.isTerminal = func() bool { return true }
	// If the prompt were shown, this empty stdin would make it error out (not a valid y/n answer), and the marker
	// would not be created. Asserting the marker exists therefore also asserts the prompt did not hang/err.
	cli.Stdin = bytes.NewBufferString("")

	// Simulate skills having already been installed, e.g. by an earlier CLI version.
	require.Nil(t, writeSkillsManifest(cli.config.homeDir, &skillsManifest{Ref: "ref", Skills: []installedSkill{{SkillName: "app-package", Harnesses: []string{"claude-code"}}}}))

	require.Nil(t, cli.Run("config", "get"))
	assert.NotContains(t, stdout.String(), "Install Vespa AI-assistant skills")
	assert.FileExists(t, marker)
}

func TestMaybePromptSkillsInstallAccepts(t *testing.T) {
	chdirTemp(t)
	cli, stdout, _ := newTestCLI(t)
	marker := filepath.Join(cli.config.homeDir, skillsPromptedMarker)
	require.Nil(t, os.Remove(marker))
	cli.isTerminal = func() bool { return true }
	// Answers, in order: accept the prompt, install all harnesses, install for this project only.
	cli.Stdin = bytes.NewBufferString("y\na\np\n")
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	require.Nil(t, cli.Run("config", "get"))
	assert.Contains(t, stdout.String(), "Installed")
	assert.FileExists(t, filepath.Join(".claude", "skills", "schema-authoring", "SKILL.md"))
	assert.FileExists(t, filepath.Join(".agents", "skills", "app-package", "SKILL.md"))
	assert.FileExists(t, filepath.Join(".cursor", "skills", "schema-authoring", "SKILL.md"))
}
