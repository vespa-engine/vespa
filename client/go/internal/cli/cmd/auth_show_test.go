// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestAuthShow(t *testing.T) {
	t.Run("auth show", func(t *testing.T) {
		testAuthShow(t, []string{"auth", "show"})
	})
	// FIXME: Enable when api-key command supports target flag
	// t.Run("auth show with target flag", func(t *testing.T) {
	// 	testAuthShowWithTargetFlag(t)
	// })
}

func testAuthShow(t *testing.T, subcommand []string) {
	cli, stdout, stderr := newTestCLI(t)

	err := cli.Run("config", "set", "target", "cloud")
	assert.Nil(t, err)
	err = cli.Run("config", "set", "application", "t1.a1")
	assert.Nil(t, err)
	err = cli.Run("auth", "api-key")
	assert.Nil(t, err)
	stdout.Reset()
	stderr.Reset()

	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `{"user":{"email":"foo@bar"}}`)
	cli.httpClient = httpClient

	err = cli.Run(subcommand...)
	assert.Nil(t, err)
	assert.Contains(t, stderr.String(), "Authenticating with API key")
	assert.Contains(t, stdout.String(), "Logged in as: foo@bar\n")
}

func testAuthShowWithTargetFlag(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)

	// Set up application but don't set target via config
	err := cli.Run("config", "set", "application", "t1.a1")
	assert.Nil(t, err)
	err = cli.Run("auth", "api-key", "--target", "local")
	assert.Nil(t, err)
	stdout.Reset()
	stderr.Reset()

	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `{"user":{"email":"foo@bar"},"tenants":{"tenant1":{"roles":["admin","developer"]}}}`)
	cli.httpClient = httpClient

	// Expect failure when target is local (default)
	err = cli.Run("auth", "show")
	assert.NotNil(t, err)
	assert.Contains(t, err.Error(), "Target must be set to 'cloud' to use this command")
	stdout.Reset()
	stderr.Reset()

	// Use --target flag directly instead of config
	err = cli.Run("auth", "show", "--target", "cloud")
	assert.Nil(t, err)
	assert.Contains(t, stderr.String(), "Authenticating with API key")
	assert.Contains(t, stdout.String(), "Logged in as: foo@bar")
	assert.Contains(t, stdout.String(), "Available tenant: tenant1")
	assert.Contains(t, stdout.String(), "your roles: admin developer")
}
