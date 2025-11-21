// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestMD5SimpleInputs(t *testing.T) {
	assert.Equal(t, "d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
	assert.Equal(t, "4044e8209f286312a68bbb54f8714922", md5Hex("admin/cluster-controllers/0\n"))
}

func TestParseJavaVersion(t *testing.T) {
	majorVersion := parseJavaVersion(`openjdk version "17.0.16" 2025-10-21
                        OpenJDK Runtime Environment Homebrew (build 17.0.16+0)
                        OpenJDK 64-Bit Server VM Homebrew (build 17.0.16+0, mixed mode, sharing)`)
	assert.Equal(t, 17, majorVersion)

	majorVersion_for_1_8 := parseJavaVersion(`openjdk version "1.8.16" 2015-10-21
                            OpenJDK Runtime Environment Homebrew (build 1.8.16+0)
                            OpenJDK 64-Bit Server VM Homebrew (build 1.8.16+0, mixed mode, sharing)`)
	assert.Equal(t, 8, majorVersion_for_1_8)
}
