// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestParseZipEtag(t *testing.T) {
	assert.Equal(t, "", parseZipEtag("vespa-skills-main.zip"))
	assert.Equal(t, "abc123", parseZipEtag("vespa-skills-main_abc123.zip"))
	// An entity tag containing an underscore must be recovered in full, not truncated at the first one.
	assert.Equal(t, "ab_cd", parseZipEtag("vespa-skills-main_ab_cd.zip"))
}

func TestSkillsArchiveRefHandlesEtagWithUnderscore(t *testing.T) {
	assert.Equal(t, "ab_cd", skillsArchiveRef("/some/cache/dir/vespa-skills-main_ab_cd.zip"))
}
