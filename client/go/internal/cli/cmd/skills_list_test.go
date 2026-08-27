// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

// testdataDir is resolved once, before any test has a chance to change the working directory (e.g. via
// chdirTemp), so that readSkillsFixture keeps working regardless of test execution order.
var testdataDir = func() string {
	dir, err := os.Getwd()
	if err != nil {
		panic(err)
	}
	return filepath.Join(dir, "testdata")
}()

func readSkillsFixture(t *testing.T) []byte {
	t.Helper()
	b, err := os.ReadFile(filepath.Join(testdataDir, "vespa-skills-fixture.zip"))
	require.Nil(t, err)
	return b
}

func TestSkillsList(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	httpClient := &mock.HTTPClient{}
	cli.httpClient = httpClient
	httpClient.NextResponseBytes(200, readSkillsFixture(t))

	require.Nil(t, cli.Run("skills", "list"))
	assert.Contains(t, stdout.String(), "app-package")
	assert.Contains(t, stdout.String(), "Scaffold and configure Vespa application packages.")
	assert.Contains(t, stdout.String(), "schema-authoring")
	assert.Contains(t, stdout.String(), "Writing, validating, and evolving Vespa .sd schema files.")
	assert.Empty(t, stderr.String())
}
