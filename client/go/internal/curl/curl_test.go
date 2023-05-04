// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package curl

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPost(t *testing.T) {
	c, err := Post("https://example.com")
	require.Nil(t, err)
	c.PrivateKey = "key.pem"
	c.Certificate = "cert.pem"
	c.WithBodyFile("file.json")
	c.Header("Content-Type", "application/json")

	assert.Equal(t, "curl --key key.pem --cert cert.pem -X POST -H 'Content-Type: application/json' --data-binary @file.json https://example.com", c.String())
}

func TestGet(t *testing.T) {
	c, err := Get("https://example.com")
	require.Nil(t, err)
	c.PrivateKey = "key.pem"
	c.Certificate = "cert.pem"
	c.Param("yql", "select * from sources * where title contains 'foo';")
	c.Param("hits", "5")

	assert.Equal(t, `curl --key key.pem --cert cert.pem 'https://example.com?hits=5&yql=select+%2A+from+sources+%2A+where+title+contains+%27foo%27%3B'`, c.String())
}

func TestRawArgs(t *testing.T) {
	c, err := RawArgs("https://example.com/search", "-v", "-m", "10", "-H", "foo: bar")
	assert.Nil(t, err)
	c.PrivateKey = "key.pem"
	c.Certificate = "cert.pem"

	assert.Equal(t, `curl --key key.pem --cert cert.pem -v -m 10 -H 'foo: bar' https://example.com/search`, c.String())
}
