// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

// TestTargetFlagPrecedence verifies that --target flag properly overrides config across all commands
func TestTargetFlagPrecedence(t *testing.T) {
	t.Run("auth api-key respects --target flag", func(t *testing.T) {
		cli, stdout, stderr := newTestCLI(t)

		// Set config to local (should be overridden by --target flag)
		err := cli.Run("config", "set", "target", "local")
		assert.Nil(t, err)

		// Use --target=cloud flag to override config
		err = cli.Run("auth", "api-key", "-a", "t1.a1.i1", "--target", "cloud")
		assert.Nil(t, err)
		assert.Equal(t, "", stderr.String())
		assert.Contains(t, stdout.String(), "Success: Developer private key for tenant t1 written to")
	})

	t.Run("auth show respects --target flag", func(t *testing.T) {
		cli, stdout, stderr := newTestCLI(t)

		// Set up for cloud target
		err := cli.Run("config", "set", "application", "t1.a1")
		assert.Nil(t, err)
		err = cli.Run("auth", "api-key", "--target", "cloud")
		assert.Nil(t, err)
		stdout.Reset()
		stderr.Reset()

		// Set config to local but use --target=cloud flag
		err = cli.Run("config", "set", "target", "local")
		assert.Nil(t, err)

		// Mock HTTP response for auth show
		httpClient := &mock.HTTPClient{}
		httpClient.NextResponseString(200, `{"user":{"email":"foo@bar"},"tenants":{"tenant1":{"roles":["admin","developer"]}}}`)
		cli.httpClient = httpClient

		// Should succeed with --target=cloud even though config is local
		err = cli.Run("auth", "show", "--target", "cloud")
		assert.Nil(t, err)
		assert.Contains(t, stdout.String(), "Logged in as: foo@bar")
	})
}
