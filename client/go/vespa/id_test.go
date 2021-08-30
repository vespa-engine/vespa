package vespa

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIdToUrlPath(t *testing.T) {
	assertIdToUrlPath("ns/type/docid/local", "id:ns:type::local", t)
	assertIdToUrlPath("ns/type/number/123/docid/local", "id:ns:type:n=123:local", t)
	assertIdToUrlPath("ns/type/group/mygroup/docid/local", "id:ns:type:g=mygroup:local", t)
}

func assertIdToUrlPath(expectedPath string, id string, t *testing.T) {
	path, err := IdToUrlPath(id)
	assert.Nil(t, err)
	assert.Equal(t, expectedPath, path)
}
