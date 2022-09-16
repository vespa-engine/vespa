// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/mock"
)

func TestVersion(t *testing.T) {
	c := &mock.HTTPClient{}
	c.NextResponseString(200, `[{"tag_name": "v1.2.3", "published_at": "2021-09-10T12:00:00Z"}]`)

	sp := &mock.Exec{}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = c
	cli.exec = sp
	cli.isTerminal = func() bool { return true }
	if err := cli.Run("version", "--color", "never"); err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, "", stderr.String())
	assert.Contains(t, stdout.String(), "Vespa CLI version 0.0.0-devel compiled with")
	assert.Contains(t, stdout.String(), "New release available: 1.2.3\nhttps://github.com/vespa-engine/vespa/releases/tag/v1.2.3")
}

func TestVersionCheckHomebrew(t *testing.T) {
	c := &mock.HTTPClient{}
	c.NextResponseString(200, `[{"tag_name": "v1.2.3", "published_at": "2021-09-10T12:00:00Z"}]`)

	sp := &mock.Exec{ProgramPath: "/usr/local/bin/vespa", CombinedOutput: "/usr/local"}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = c
	cli.exec = sp
	cli.isTerminal = func() bool { return true }
	if err := cli.Run("version", "--color", "never"); err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, "", stderr.String())
	assert.Contains(t, stdout.String(), "Vespa CLI version 0.0.0-devel compiled with")
	assert.Contains(t, stdout.String(), "New release available: 1.2.3\n"+
		"https://github.com/vespa-engine/vespa/releases/tag/v1.2.3\n"+
		"\nUpgrade by running:\nbrew update && brew upgrade vespa-cli\n")
}
