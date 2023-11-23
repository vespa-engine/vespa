// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestDestroy(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t, "NO_COLOR=true", "CI=true")
	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, "ok")
	httpClient.NextResponseString(200, "ok")
	cli.httpClient = httpClient
	cli.isTerminal = func() bool { return true }
	var buf bytes.Buffer
	cli.Stdin = &buf

	require.Nil(t, cli.Run("config", "set", "target", "cloud"))
	require.Nil(t, cli.Run("config", "set", "application", "foo.bar.baz"))
	require.Nil(t, cli.Run("auth", "api-key"))

	// No removal without confirmation
	stdout.Reset()
	stderr.Reset()
	buf.WriteString("\n")
	require.NotNil(t, cli.Run("destroy", "-z", "dev.aws-us-east-1c"))
	warning := "Warning: This operation will irrecoverably remove the deployment of foo.bar.baz in dev.aws-us-east-1c and all of its data"
	confirmation := "Proceed with removal? [y/N] "
	assert.Equal(t, warning+"\nError: refusing to remove deployment of foo.bar.baz in dev.aws-us-east-1c without confirmation\n", stderr.String())
	assert.Equal(t, confirmation, stdout.String())

	// Removes deployment with confirmation
	stdout.Reset()
	stderr.Reset()
	buf.WriteString("y\n")
	require.Nil(t, cli.Run("destroy", "-z", "dev.aws-us-east-1c"))
	success := "Success: Removed deployment of foo.bar.baz in dev.aws-us-east-1c\n"
	assert.Equal(t, confirmation+success, stdout.String())

	// Force flag always removes deployment
	stdout.Reset()
	stderr.Reset()
	require.Nil(t, cli.Run("destroy", "-z", "dev.aws-us-east-1c", "--force"))
	assert.Equal(t, success, stdout.String())

	// Cannot remove a prod deployment
	require.NotNil(t, cli.Run("destroy", "-z", "prod.aws-us-east-1c"))
	assert.Equal(t, "Error: cannot remove production deployment of foo.bar.baz in prod.aws-us-east-1c\nHint: See https://cloud.vespa.ai/en/deleting-applications\n", stderr.String())

	// Cannot remove a local deployment at all
	stderr.Reset()
	require.Nil(t, cli.Run("config", "set", "target", "local"))
	require.Nil(t, cli.Run("config", "set", "application", "foo.bar.baz"))
	require.NotNil(t, cli.Run("destroy", "-z", "prod.aws-us-east-1c"))
	assert.Equal(t, "Error: command does not support local target\n", stderr.String())
}
