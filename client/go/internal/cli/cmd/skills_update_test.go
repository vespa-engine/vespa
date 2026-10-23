// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"net/http"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func etagResponse(etag string, body []byte) mock.HTTPResponse {
	headers := make(http.Header)
	headers.Set("etag", `W/"`+etag+`"`)
	return mock.HTTPResponse{Status: 200, Body: body, Header: headers}
}

func TestSkillsUpdate(t *testing.T) {
	chdirTemp(t)
	cli, stdout, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	fixture := readSkillsFixture(t)

	httpClient.NextResponse(etagResponse("v1", fixture))
	require.Nil(t, cli.Run("skills", "install", "schema-authoring", "--harness", "claude", "--local"))

	// A new version is available upstream (different ETag) - update should overwrite the installed skill.
	httpClient.NextResponse(etagResponse("v2", fixture))
	require.Nil(t, cli.Run("skills", "update"))
	assert.Contains(t, stdout.String(), "Updated 1 skill installation(s)")
	assert.FileExists(t, filepath.Join(".claude", "skills", "schema-authoring", "SKILL.md"))
	stdout.Reset()

	// Nothing changed upstream (same ETag) - update should be a no-op.
	httpClient.NextResponse(etagResponse("v2", fixture))
	require.Nil(t, cli.Run("skills", "update"))
	assert.Contains(t, stderr.String(), "Skills are already up to date")
}

func TestSkillsUpdateUnknownSkill(t *testing.T) {
	chdirTemp(t)
	cli, stdout, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	fixture := readSkillsFixture(t)

	httpClient.NextResponse(etagResponse("v1", fixture))
	require.Nil(t, cli.Run("skills", "install", "schema-authoring", "--harness", "claude", "--local"))

	// A misspelled/never-installed skill name must error, not silently report "already up to date".
	stdout.Reset()
	httpClient.NextResponse(etagResponse("v1", fixture))
	require.NotNil(t, cli.Run("skills", "update", "schema-authroing"))
	assert.Contains(t, stderr.String(), "unknown skill(s): schema-authroing")
	assert.NotContains(t, stderr.String(), "already up to date")
	assert.Empty(t, stdout.String())
}

func TestSkillsUpdateWithoutInstall(t *testing.T) {
	chdirTemp(t)
	cli, _, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponse(etagResponse("v1", readSkillsFixture(t)))

	require.NotNil(t, cli.Run("skills", "update"))
	assert.Contains(t, stderr.String(), "no installed skills found")
	assert.Contains(t, stderr.String(), "Run 'vespa skills install' first")
}
