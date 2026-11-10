// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestSkillsGet(t *testing.T) {
	assertSkillsGet(t, "http://127.0.0.1:8080/skills/v1")
	assertSkillsGet(t, "http://127.0.0.1:8080/skills/v1", "/skills/v1/")
	assertSkillsGet(t, "http://127.0.0.1:8080/skills/v1/schemas", "schemas")
	assertSkillsGet(t, "http://127.0.0.1:8080/skills/v1/schemas/music", "schemas/music")
	assertSkillsGet(t, "http://127.0.0.1:8080/skills/v1/schemas/music", "/skills/v1/schemas/music/")
}

func TestSkillsGetVerbose(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(200, "# Skills\n")
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	require.Nil(t, cli.Run("-t", "http://127.0.0.1:8080", "skills", "get", "-v", "schemas"))
	assert.Equal(t, "curl http://127.0.0.1:8080/skills/v1/schemas\n", stderr.String())
	assert.Equal(t, "# Skills\n", stdout.String())
}

func TestSkillsGetNotFound(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextResponseString(404, `{"error-code":"NOT_FOUND","message":"No schema named 'foo'"}`)
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "skills", "get", "schemas/foo"))
	assert.Empty(t, stdout.String())
	assert.Equal(t, "Error: Status 404 getting /skills/v1/schemas/foo\n{\"error-code\":\"NOT_FOUND\",\"message\":\"No schema named 'foo'\"}\n", stderr.String())
}

func TestSkillsGetNoSkills(t *testing.T) {
	client := &mock.HTTPClient{}
	client.NextStatus(404)
	cli, _, stderr := newTestCLI(t)
	cli.httpClient = client

	assert.NotNil(t, cli.Run("-t", "http://127.0.0.1:8080", "skills", "get"))
	assert.Contains(t, stderr.String(), "this application serves no skills")
	assert.Contains(t, stderr.String(), "<skills><schemas/></skills>")
}

func assertSkillsGet(t *testing.T, expectedURL string, args ...string) {
	t.Helper()
	client := &mock.HTTPClient{}
	client.NextResponseString(200, "# Skill\n")
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = client

	require.Nil(t, cli.Run(append([]string{"-t", "http://127.0.0.1:8080", "skills", "get"}, args...)...))
	assert.Equal(t, expectedURL, client.LastRequest.URL.String())
	assert.Equal(t, "# Skill\n", stdout.String())
	assert.Empty(t, stderr.String())
}
