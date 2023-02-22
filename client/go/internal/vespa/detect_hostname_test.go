// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func TestDetectHostname(t *testing.T) {
	trace.AdjustVerbosity(0)
	t.Setenv("VESPA_HOSTNAME", "foo.bar")
	got, err := FindOurHostname()
	assert.Nil(t, err)
	assert.Equal(t, "foo.bar", got)
	os.Unsetenv("VESPA_HOSTNAME")
	got, _ = FindOurHostname()
	assert.NotEqual(t, "", got)
	fmt.Fprintln(os.Stderr, "FindOurHostname() returns:", got, "with error:", err)
}
