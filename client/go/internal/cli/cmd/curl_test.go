// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCurl(t *testing.T) {
	cli, stdout, _ := newTestCLI(t)
	cli.Environment["VESPA_CLI_ENDPOINTS"] = "{\"endpoints\":[{\"cluster\":\"container\",\"url\":\"http://127.0.0.1:8080\"}]}"
	assert.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	assert.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Nil(t, cli.Run("config", "set", "cluster", "container"))
	assert.Nil(t, cli.Run("auth", "api-key"))
	assert.Nil(t, cli.Run("auth", "cert", "--no-add"))

	stdout.Reset()
	err := cli.Run("curl", "-n", "--", "-v", "--data-urlencode", "arg=with space", "/search")
	assert.Nil(t, err)

	expected := fmt.Sprintf("curl --key %s --cert %s -v --data-urlencode 'arg=with space' http://127.0.0.1:8080/search\n",
		filepath.Join(cli.config.homeDir, "t1.a1.i1", "data-plane-private-key.pem"),
		filepath.Join(cli.config.homeDir, "t1.a1.i1", "data-plane-public-cert.pem"))
	assert.Equal(t, expected, stdout.String())

	assert.Nil(t, cli.Run("config", "set", "target", "local"))

	stdout.Reset()
	err = cli.Run("curl", "-a", "t1.a1.i1", "-s", "deploy", "-n", "/application/v4/tenant/foo")
	assert.Nil(t, err)
	expected = "curl http://127.0.0.1:19071/application/v4/tenant/foo\n"
	assert.Equal(t, expected, stdout.String())
}
