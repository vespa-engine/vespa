// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/mock"
	"github.com/vespa-engine/vespa/client/go/version"
)

func TestLog(t *testing.T) {
	_, pkgDir := mock.ApplicationPackageDir(t, false, false)
	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `1632738690.905535	host1a.dev.aws-us-east-1c	806/53	logserver-container	Container.com.yahoo.container.jdisc.ConfiguredApplication	info	Switching to the latest deployed set of configurations and components. Application config generation: 52532`)
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = httpClient

	assert.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", pkgDir))

	stdout.Reset()
	assert.Nil(t, cli.Run("log", "--from", "2021-09-27T10:00:00Z", "--to", "2021-09-27T11:00:00Z"))
	expected := "[2021-09-27 10:31:30.905535] host1a.dev.aws-us-east-1c info    logserver-container Container.com.yahoo.container.jdisc.ConfiguredApplication	Switching to the latest deployed set of configurations and components. Application config generation: 52532\n"
	assert.Equal(t, expected, stdout.String())

	assert.NotNil(t, cli.Run("log", "--from", "2021-09-27T13:12:49Z", "--to", "2021-09-27T13:15:00", "1h"))
	assert.Contains(t, stderr.String(), "Error: invalid period: cannot combine --from/--to with relative value: 1h\n")
}

func TestLogOldClient(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	cli.version = version.MustParse("7.0.0")

	_, pkgDir := mock.ApplicationPackageDir(t, false, false)
	httpClient := &mock.HTTPClient{}
	httpClient.NextResponseString(200, `{"minVersion": "8.0.0"}`)
	httpClient.NextResponseString(200, `1632738690.905535	host1a.dev.aws-us-east-1c	806/53	logserver-container	Container.com.yahoo.container.jdisc.ConfiguredApplication	info	Switching to the latest deployed set of configurations and components. Application config generation: 52532`)
	cli.httpClient = httpClient

	assert.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", pkgDir))

	assert.Nil(t, cli.Run("log"))
	expected := "Warning: client version 7.0.0 is less than the minimum supported version: 8.0.0\nHint: This version may not work as expected\nHint: Try 'vespa version' to check for a new version\n"
	assert.Contains(t, stderr.String(), expected)
}
