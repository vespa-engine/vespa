// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/util"
)

func TestVersion(t *testing.T) {
	c := &mockHttpClient{}
	c.NextResponse(200, `[{"tag_name": "v1.2.3", "published_at": "2021-09-10T12:00:00Z"}]`)
	util.ActiveHttpClient = c

	sp = &mockSubprocess{}
	out, _ := execute(command{args: []string{"version"}}, t, nil)
	assert.Contains(t, out, "vespa version 0.0.0-devel compiled with")
	assert.Contains(t, out, "New release available: 1.2.3\nhttps://github.com/vespa-engine/vespa/releases/tag/v1.2.3")
}

func TestVersionCheckHomebrew(t *testing.T) {
	c := &mockHttpClient{}
	c.NextResponse(200, `[{"tag_name": "v1.2.3", "published_at": "2021-09-10T12:00:00Z"}]`)
	util.ActiveHttpClient = c

	sp = &mockSubprocess{programPath: "/usr/local/bin/vespa", output: "/usr/local"}
	out, _ := execute(command{args: []string{"version"}}, t, nil)
	assert.Contains(t, out, "vespa version 0.0.0-devel compiled with")
	assert.Contains(t, out, "New release available: 1.2.3\n"+
		"https://github.com/vespa-engine/vespa/releases/tag/v1.2.3\n"+
		"\nUpgrade by running:\nbrew update && brew upgrade vespa-cli\n")
}

type mockSubprocess struct {
	programPath string
	output      string
}

func (c *mockSubprocess) pathOf(name string) (string, error) {
	if c.programPath == "" {
		return "", fmt.Errorf("no program path set in this mock")
	}
	return c.programPath, nil
}

func (c *mockSubprocess) outputOf(name string, args ...string) ([]byte, error) {
	return []byte(c.output), nil
}

func (c *mockSubprocess) isTerminal() bool { return true }
