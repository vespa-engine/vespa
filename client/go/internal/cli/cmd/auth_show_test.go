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
