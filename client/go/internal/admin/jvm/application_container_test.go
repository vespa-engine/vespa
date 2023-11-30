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
