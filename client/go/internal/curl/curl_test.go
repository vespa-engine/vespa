// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package curl

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPost(t *testing.T) {
	c, err := Post("https://example.invalid")
	require.Nil(t, err)
	c.PrivateKey = "/home/my path/key#.pem"
	c.Certificate = "/home/my path/cert!.pem"
	c.Timeout = time.Minute
	c.WithBodyFile("file.json")
	c.Header("Content-Type", "application/json")

	assert.Equal(t, "curl --key '/home/my path/key#.pem' --cert '/home/my path/cert!.pem' -X POST -m 60 -H 'Content-Type: application/json' --data-binary @file.json https://example.invalid", c.String())
}

func TestGet(t *testing.T) {
	c, err := Get("https://example.invalid")
	require.Nil(t, err)
	c.PrivateKey = "key.pem"
	c.Certificate = "cert.pem"
	c.Param("yql", "select * from sources * where title contains 'foo'")
	c.Param("hits", "5")

	assert.Equal(t, `curl --key key.pem --cert cert.pem 'https://example.invalid?hits=5&yql=select+%2A+from+sources+%2A+where+title+contains+%27foo%27'`, c.String())
}

func TestRawArgs(t *testing.T) {
	c, err := RawArgs("https://example.invalid/search", "-v", "-m", "10", "-H", "foo: bar")
	assert.Nil(t, err)
	c.PrivateKey = "key.pem"
	c.Certificate = "cert.pem"

	assert.Equal(t, `curl --key key.pem --cert cert.pem -v -m 10 -H 'foo: bar' https://example.invalid/search`, c.String())
}
