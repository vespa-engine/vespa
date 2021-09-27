// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCurl(t *testing.T) {
	homeDir := filepath.Join(t.TempDir(), ".vespa")
	httpClient := &mockHttpClient{}
	out, _ := execute(command{homeDir: homeDir, args: []string{"curl", "-n", "-a", "t1.a1.i1", "--", "-v", "--data-urlencode", "arg=with space", "/search"}}, t, httpClient)

	expected := fmt.Sprintf("curl --key %s --cert %s -v --data-urlencode 'arg=with space' https://127.0.0.1:8080/search\n",
		filepath.Join(homeDir, "t1.a1.i1", "data-plane-private-key.pem"),
		filepath.Join(homeDir, "t1.a1.i1", "data-plane-public-cert.pem"))
	assert.Equal(t, expected, out)
}
