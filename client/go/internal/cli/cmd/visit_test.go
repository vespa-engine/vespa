// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestQuoteFunc(t *testing.T) {
	var buf []byte = make([]byte, 3)
	buf[0] = 'a'
	buf[2] = 'z'
	for i := 0; i < 256; i++ {
		buf[1] = byte(i)
		s := string(buf)
		res := quoteArgForUrl(s)
		if i < 32 || i > 127 {
			assert.Equal(t, "a+z", res)
		} else {
			fmt.Printf("res %3d => '%s'\n", i, res)
		}
	}
}
