// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIdToURLPath(t *testing.T) {
	assertIdToURLPath("ns/type/docid/local", "id:ns:type::local", t)
	assertIdToURLPath("ns/type/number/123/docid/local", "id:ns:type:n=123:local", t)
	assertIdToURLPath("ns/type/group/mygroup/docid/local", "id:ns:type:g=mygroup:local", t)
}

func assertIdToURLPath(expectedPath string, id string, t *testing.T) {
	path, err := IdToURLPath(id)
	assert.Nil(t, err)
	assert.Equal(t, expectedPath, path)
}
