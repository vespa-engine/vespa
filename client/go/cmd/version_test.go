package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestVersion(t *testing.T) {
	assert.Contains(t, execute(command{args: []string{"version"}}, t, nil), "vespa version 0.0.0-devel compiled with")
}
