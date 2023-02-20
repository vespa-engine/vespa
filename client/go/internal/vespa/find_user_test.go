// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestFindVespaUser(t *testing.T) {
	var uName string

	t.Setenv("VESPA_USER", "nobody")
	uName = FindVespaUser()
	assert.Equal(t, "nobody", uName)

	t.Setenv("VESPA_USER", "")
	uName = FindVespaUser()
	assert.NotEqual(t, "", uName)
}

func TestFindVespaUidAndGid(t *testing.T) {
	uid, gid := FindVespaUidAndGid()
	fmt.Fprintln(os.Stderr, "INFO: result from FindVespaUidAndGid() is", uid, "and", gid)
}
