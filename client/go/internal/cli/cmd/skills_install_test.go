// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

// chdirTemp changes the working directory to a new temporary directory for the duration of the test.
func chdirTemp(t *testing.T) string {
	t.Helper()
	origWd, err := os.Getwd()
	require.Nil(t, err)
	dir := t.TempDir()
	require.Nil(t, os.Chdir(dir))
	t.Cleanup(func() { os.Chdir(origWd) })
	return dir
}

func TestSkillsInstallLocal(t *testing.T) {
	chdirTemp(t)
	cli, stdout, _ := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	require.Nil(t, cli.Run("skills", "install", "schema-authoring", "--harness", "claude", "--local"))
	assert.Contains(t, stdout.String(), "Installed schema-authoring for Claude Code")

	skillMd := filepath.Join(".claude", "skills", "schema-authoring", "SKILL.md")
	assert.FileExists(t, skillMd)
	assert.FileExists(t, filepath.Join(".claude", "skills", "schema-authoring", "docs", "field-types.md"))
	// app-package was not requested, so it should not have been installed
	assert.NoDirExists(t, filepath.Join(".claude", "skills", "app-package"))

	manifestPath := filepath.Join(".vespa", "skills-lock.json")
	require.FileExists(t, manifestPath)
	b, err := os.ReadFile(manifestPath)
	require.Nil(t, err)
	var manifest skillsManifest
	require.Nil(t, json.Unmarshal(b, &manifest))
	require.Len(t, manifest.Skills, 1)
	assert.Equal(t, "schema-authoring", manifest.Skills[0].SkillName)
	assert.Equal(t, []string{"claude"}, manifest.Skills[0].Harnesses)
}

func TestSkillsInstallAllSkillsByDefault(t *testing.T) {
	chdirTemp(t)
	cli, _, _ := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	require.Nil(t, cli.Run("skills", "install", "--harness", "claude", "--local"))
	assert.FileExists(t, filepath.Join(".claude", "skills", "schema-authoring", "SKILL.md"))
	assert.FileExists(t, filepath.Join(".claude", "skills", "app-package", "SKILL.md"))
}

func TestSkillsInstallDedupesSharedHarnessDir(t *testing.T) {
	chdirTemp(t)
	cli, _, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	// codex and antigravity resolve to the same directory (.agents/skills) - it should only be written once.
	require.Nil(t, cli.Run("skills", "install", "schema-authoring", "--harness", "codex,antigravity", "--local"))
	dest := filepath.Join(".agents", "skills", "schema-authoring")
	assert.FileExists(t, filepath.Join(dest, "SKILL.md"))
	assert.Equal(t, 1, strings.Count(stderr.String(), dest))
}

func TestSkillsInstallGlobal(t *testing.T) {
	chdirTemp(t)
	fakeHome := t.TempDir()
	t.Setenv("HOME", fakeHome)
	cli, stdout, _ := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	require.Nil(t, cli.Run("skills", "install", "app-package", "--harness", "cursor", "--global"))
	assert.Contains(t, stdout.String(), "Installed app-package for Cursor")
	assert.FileExists(t, filepath.Join(fakeHome, ".cursor", "skills", "app-package", "SKILL.md"))
	// Global install must not touch the current project
	assert.NoDirExists(t, ".cursor")
}

func TestSkillsInstallUnknownSkill(t *testing.T) {
	chdirTemp(t)
	cli, _, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	require.NotNil(t, cli.Run("skills", "install", "does-not-exist", "--harness", "claude", "--local"))
	assert.Contains(t, stderr.String(), "unknown skill(s): does-not-exist")
}

func TestSkillsInstallUnknownHarness(t *testing.T) {
	chdirTemp(t)
	cli, _, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	require.NotNil(t, cli.Run("skills", "install", "--harness", "does-not-exist", "--local"))
	assert.Contains(t, stderr.String(), "unknown harness(es): does-not-exist")
}

func TestSkillsInstallNonInteractiveRequiresFlags(t *testing.T) {
	chdirTemp(t)
	cli, _, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	// cli.isTerminal() is false by default in tests (Stdout/Stderr are not *os.File), simulating a non-interactive
	// invocation (e.g. in CI) with no --harness flag given.
	require.NotNil(t, cli.Run("skills", "install", "--local"))
	assert.Contains(t, stderr.String(), "no harness specified")
}

func TestSkillsInstallConflictRequiresForce(t *testing.T) {
	chdirTemp(t)
	cli, _, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	fixture := readSkillsFixture(t)
	httpClient.NextResponse(etagResponse("abc", fixture))
	require.Nil(t, cli.Run("skills", "install", "schema-authoring", "--harness", "claude", "--local"))

	// Second install without --force: cache is still valid (conditional GET returns 304), but the destination
	// directory already exists and is non-empty, so it should fail.
	httpClient.NextStatus(http.StatusNotModified)
	require.NotNil(t, cli.Run("skills", "install", "schema-authoring", "--harness", "claude", "--local"))
	assert.Contains(t, stderr.String(), "already exists and is not empty")
	stderr.Reset()

	// --force bypasses the cache (so a full response body is required) and overwrites the destination.
	httpClient.NextResponse(etagResponse("abc", fixture))
	require.Nil(t, cli.Run("skills", "install", "schema-authoring", "--harness", "claude", "--local", "--force"))
}

func TestSkillsInstallPartialFailureStillRecordsCompletedSkills(t *testing.T) {
	chdirTemp(t)
	cli, _, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient

	// app-package's destination already exists and is non-empty, so extraction will fail on it. schema-authoring
	// is listed first and has no such conflict, so it should succeed and be persisted to the manifest even though
	// the overall command fails on the second skill.
	require.Nil(t, os.MkdirAll(filepath.Join(".claude", "skills", "app-package", "existing-file"), 0o755))
	httpClient.NextResponseBytes(200, readSkillsFixture(t))
	require.NotNil(t, cli.Run("skills", "install", "schema-authoring", "app-package", "--harness", "claude", "--local"))
	assert.Contains(t, stderr.String(), "already exists and is not empty")

	assert.FileExists(t, filepath.Join(".claude", "skills", "schema-authoring", "SKILL.md"))

	manifestPath := filepath.Join(".vespa", "skills-lock.json")
	require.FileExists(t, manifestPath)
	b, err := os.ReadFile(manifestPath)
	require.Nil(t, err)
	var manifest skillsManifest
	require.Nil(t, json.Unmarshal(b, &manifest))
	require.Len(t, manifest.Skills, 1)
	assert.Equal(t, "schema-authoring", manifest.Skills[0].SkillName)
}
