// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/mock"
)

func TestCurl(t *testing.T) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	httpClient := &mock.HTTPClient{}
	_, outErr := execute(command{args: []string{"config", "set", "application", "t1.a1.i1"}, homeDir: homeDir}, t, nil)
	assert.Equal(t, "", outErr)
	_, outErr = execute(command{args: []string{"config", "set", "target", "cloud"}, homeDir: homeDir}, t, nil)
	assert.Equal(t, "", outErr)
	_, outErr = execute(command{args: []string{"auth", "api-key"}, homeDir: homeDir}, t, nil)
	assert.Equal(t, "", outErr)
	_, outErr = execute(command{args: []string{"auth", "cert", "--no-add"}, homeDir: homeDir}, t, nil)
	assert.Equal(t, "", outErr)

	os.Setenv("VESPA_CLI_ENDPOINTS", "{\"endpoints\":[{\"cluster\":\"container\",\"url\":\"http://127.0.0.1:8080\"}]}")
	out, _ := execute(command{homeDir: homeDir, args: []string{"curl", "-n", "--", "-v", "--data-urlencode", "arg=with space", "/search"}}, t, httpClient)

	expected := fmt.Sprintf("curl --key %s --cert %s -v --data-urlencode 'arg=with space' http://127.0.0.1:8080/search\n",
		filepath.Join(homeDir, "t1.a1.i1", "data-plane-private-key.pem"),
		filepath.Join(homeDir, "t1.a1.i1", "data-plane-public-cert.pem"))
	assert.Equal(t, expected, out)

	_, outErr = execute(command{args: []string{"config", "set", "target", "local"}, homeDir: homeDir}, t, nil)
	assert.Equal(t, "", outErr)
	out, outErr = execute(command{homeDir: homeDir, args: []string{"curl", "-a", "t1.a1.i1", "-s", "deploy", "-n", "/application/v4/tenant/foo"}}, t, httpClient)
	assert.Equal(t, "", outErr)
	expected = "curl http://127.0.0.1:19071/application/v4/tenant/foo\n"
	assert.Equal(t, expected, out)
}
