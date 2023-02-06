// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package jvm

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestSomeQuoting(t *testing.T) {
	var buf bytes.Buffer
	propQuote("a=b", &buf)
	assert.Equal(t, "a=b", buf.String())
	buf.Reset()
	propQuote("foobar", &buf)
	assert.Equal(t, "foobar", buf.String())
	buf.Reset()
	propQuote("x y = foo bar ", &buf)
	assert.Equal(t, `x\u0020y\u0020=\u0020foo bar `, buf.String())
	buf.Reset()
	propQuote("x:y=foo:bar", &buf)
	assert.Equal(t, "x\\u003Ay=foo:bar", buf.String())
	buf.Reset()
	propQuote(`PS1=\[\e[0;32m\]AWS-US\[\e[0m\] [\u@\[\e[1m\]\h\[\e[0m\]:\w]$ `, &buf)
	assert.Equal(t, `PS1=\\[\\e[0;32m\\]AWS-US\\[\\e[0m\\] [\\u@\\[\\e[1m\\]\\h\\[\\e[0m\\]:\\w]$ `, buf.String())
}
