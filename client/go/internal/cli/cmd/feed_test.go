package cmd

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

type manualClock struct {
	t    time.Time
	tick time.Duration
}

func (c *manualClock) now() time.Time {
	t := c.t
	c.t = c.t.Add(c.tick)
	return t
}

func TestFeed(t *testing.T) {
	clock := &manualClock{tick: time.Second}
	cli, stdout, stderr := newTestCLI(t)
	httpClient := cli.httpClient.(*mock.HTTPClient)
	httpClient.ReadBody = true
	cli.now = clock.now

	td := t.TempDir()
	doc := []byte(`{
  "put": "id:ns:type::doc1",
  "fields": {"foo": "123"}
}`)
	jsonFile1 := filepath.Join(td, "docs1.jsonl")
	jsonFile2 := filepath.Join(td, "docs2.jsonl")
	require.Nil(t, os.WriteFile(jsonFile1, doc, 0644))
	require.Nil(t, os.WriteFile(jsonFile2, doc, 0644))

	httpClient.NextResponseString(200, `{"message":"OK"}`)
	httpClient.NextResponseString(200, `{"message":"OK"}`)
	require.Nil(t, cli.Run("feed", jsonFile1, jsonFile2))

	assert.Equal(t, "", stderr.String())
	want := `{
  "feeder.seconds": 5.000,
  "feeder.ok.count": 2,
  "feeder.ok.rate": 0.400,
  "feeder.error.count": 0,
  "feeder.inflight.count": 0,
  "http.request.count": 2,
  "http.request.bytes": 50,
  "http.request.MBps": 0.000,
  "http.exception.count": 0,
  "http.response.count": 2,
  "http.response.bytes": 32,
  "http.response.MBps": 0.000,
  "http.response.error.count": 0,
  "http.response.latency.millis.min": 1000,
  "http.response.latency.millis.avg": 1000,
  "http.response.latency.millis.max": 1000,
  "http.response.code.counts": {
    "200": 2
  }
}
`
	assert.Equal(t, want, stdout.String())

	stdout.Reset()
	var stdinBuf bytes.Buffer
	stdinBuf.Write(doc)
	stdinBuf.Write(doc)
	cli.Stdin = &stdinBuf
	httpClient.NextResponseString(200, `{"message":"OK"}`)
	httpClient.NextResponseString(200, `{"message":"OK"}`)
	require.Nil(t, cli.Run("feed", "-"))
	assert.Equal(t, want, stdout.String())

	for i := 0; i < 10; i++ {
		httpClient.NextResponseString(500, `{"message":"it's broken yo"}`)
	}
	require.Nil(t, cli.Run("feed", jsonFile1))
	assert.Equal(t, "feed: got status 500 ({\"message\":\"it's broken yo\"}) for put id:ns:type::doc1: giving up after 10 attempts\n", stderr.String())
	stderr.Reset()
	for i := 0; i < 10; i++ {
		httpClient.NextResponseError(fmt.Errorf("something else is broken"))
	}
	require.Nil(t, cli.Run("feed", jsonFile1))
	assert.Equal(t, "feed: got error \"something else is broken\" (no body) for put id:ns:type::doc1: giving up after 10 attempts\n", stderr.String())
}

func TestFeedInvalid(t *testing.T) {
	clock := &manualClock{tick: time.Second}
	cli, stdout, stderr := newTestCLI(t)
	httpClient := cli.httpClient.(*mock.HTTPClient)
	httpClient.ReadBody = true
	cli.now = clock.now

	td := t.TempDir()
	doc := []byte(`
{
  "put": "id:ns:type::doc1",
  "fields": {"foo": "123"}
},
{
  "put": "id:ns:type::doc2",
  "fields": {"foo": "invalid json"
}`)
	jsonFile := filepath.Join(td, "docs.jsonl")
	require.Nil(t, os.WriteFile(jsonFile, doc, 0644))
	httpClient.NextResponseString(200, `{"message":"OK"}`)
	require.NotNil(t, cli.Run("feed", jsonFile))

	want := `{
  "feeder.seconds": 3.000,
  "feeder.ok.count": 1,
  "feeder.ok.rate": 0.333,
  "feeder.error.count": 0,
  "feeder.inflight.count": 0,
  "http.request.count": 1,
  "http.request.bytes": 25,
  "http.request.MBps": 0.000,
  "http.exception.count": 0,
  "http.response.count": 1,
  "http.response.bytes": 16,
  "http.response.MBps": 0.000,
  "http.response.error.count": 0,
  "http.response.latency.millis.min": 1000,
  "http.response.latency.millis.avg": 1000,
  "http.response.latency.millis.max": 1000,
  "http.response.code.counts": {
    "200": 1
  }
}
`
	assert.Equal(t, want, stdout.String())
	assert.Contains(t, stderr.String(), "Error: failed to decode document")
}
