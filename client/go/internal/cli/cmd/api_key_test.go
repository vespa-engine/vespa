// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAPIKey(t *testing.T) {
	t.Run("auth api-key", func(t *testing.T) {
		testAPIKey(t, []string{"auth", "api-key"})
	})
	t.Run("auth api-key with target flag override", func(t *testing.T) {
		testAPIKeyWithTargetFlag(t, []string{"auth", "api-key"})
	})
}

func testAPIKey(t *testing.T, subcommand []string) {
	cli, stdout, stderr := newTestCLI(t)

	err := cli.Run("config", "set", "target", "cloud")
	assert.Nil(t, err)

	args := append(subcommand, "-a", "t1.a1.i1")
	err = cli.Run(args...)
	assert.Nil(t, err)
	assert.Equal(t, "", stderr.String())
	assert.Contains(t, stdout.String(), "Success: Developer private key for tenant t1 written to")

	err = cli.Run(subcommand...)
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), "Error: refusing to overwrite")
	assert.Contains(t, stderr.String(), "Hint: Use -f to overwrite it\n")
	assert.Contains(t, stdout.String(), "This is your public key")
}

func testAPIKeyWithTargetFlag(t *testing.T, subcommand []string) {
	cli, stdout, stderr := newTestCLI(t)

	// Set config to local (should be overridden by --target flag)
	err := cli.Run("config", "set", "target", "local")
	assert.Nil(t, err)

	// Use --target=cloud flag to override config
	args := append(subcommand, "-a", "t1.a1.i1", "--target", "cloud")
	err = cli.Run(args...)
	assert.Nil(t, err)
	assert.Equal(t, "", stderr.String())
	assert.Contains(t, stdout.String(), "Success: Developer private key for tenant t1 written to")

	err = cli.Run(append(subcommand, "--target", "cloud")...)
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), "Error: refusing to overwrite")
	assert.Contains(t, stderr.String(), "Hint: Use -f to overwrite it\n")
	assert.Contains(t, stdout.String(), "This is your public key")
}
