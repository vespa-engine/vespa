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

	// No removal without confirmation
	buf.WriteString("\n")
	require.NotNil(t, cli.Run("destroy"))
	warning := "Warning: This operation will irrecoverably remove current deployment and all of its data"
	confirmation := "Proceed with removal? [y/N] "
	assert.Equal(t, warning+"\nError: refusing to remove current deployment without confirmation\n", stderr.String())
	assert.Equal(t, confirmation, stdout.String())

	// Removes deployment with confirmation
	stdout.Reset()
	stderr.Reset()
	buf.WriteString("y\n")
	require.Nil(t, cli.Run("destroy"))
	success := "Success: Removed current deployment\n"
	assert.Equal(t, confirmation+success, stdout.String())

	// Force flag always removes deployment
	stdout.Reset()
	stderr.Reset()
	require.Nil(t, cli.Run("destroy", "--force"))
	assert.Equal(t, success, stdout.String())

	// Cannot remove prod deployment in Vespa Cloud
	stderr.Reset()
	require.Nil(t, cli.Run("config", "set", "target", "cloud"))
	require.Nil(t, cli.Run("config", "set", "application", "foo.bar.baz"))
	require.Nil(t, cli.Run("auth", "api-key"))
	require.NotNil(t, cli.Run("destroy", "-z", "prod.aws-us-east-1c"))
	assert.Equal(t, "Error: cannot remove production deployment of foo.bar.baz in prod.aws-us-east-1c\nHint: See https://cloud.vespa.ai/en/deleting-applications\n", stderr.String())
}
