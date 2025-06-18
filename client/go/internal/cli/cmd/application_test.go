// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestApplicationShow(t *testing.T) {
	t.Run("application show with target flag", func(t *testing.T) {
		testApplicationShowWithTargetFlag(t)
	})
}

func testApplicationShowWithTargetFlag(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)

	// Set config to local (should be overridden by --target flag)
	err := cli.Run("config", "set", "target", "local")
	assert.Nil(t, err)

	// Create an API key for cloud authentication (like cert_test.go does)
	err = cli.Run("auth", "api-key", "--target", "cloud", "-a", "vespa-team.factory")
	assert.Nil(t, err)
	stdout.Reset()
	stderr.Reset()

	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `{
		"tenant": "vespa-team",
		"application": "factory",
		"instances": [
			{
				"instance": "default",
				"deployments": [
					{
						"environment": "prod",
						"region": "aws-us-east-1c"
					}
				]
			}
		]
	}`)
	cli.httpClient = httpClient

	// Use --target=cloud flag to override config
	err = cli.Run("application", "show", "--target", "cloud", "-a", "vespa-team.factory")
	assert.Nil(t, err)
	// Allow the warning message in stderr
	assert.Contains(t, stderr.String(), "Authenticating with API key")
	assert.Contains(t, stdout.String(), "vespa-team.factory.default:")
	assert.Contains(t, stdout.String(), "prod.aws-us-east-1c")
}
