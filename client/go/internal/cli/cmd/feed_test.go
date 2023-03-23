package cmd

import (
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
	httpClient := &mock.HTTPClient{}
	clock := &manualClock{tick: time.Second}
	cli, stdout, stderr := newTestCLI(t)
	cli.httpClient = httpClient
	cli.now = clock.now

	td := t.TempDir()
	jsonFile := filepath.Join(td, "docs.jsonl")
	err := os.WriteFile(jsonFile, []byte(`{
  "put": "id:ns:type::doc1",
  "fields": {"foo": "123"}
}`), 0644)

	require.Nil(t, err)

	httpClient.NextResponseString(200, `{"message":"OK"}`)
	require.Nil(t, cli.Run("feed", jsonFile))

	assert.Equal(t, "", stderr.String())
	assert.Equal(t, `{
  "feeder.seconds": 1.000,
  "feeder.ok.count": 1,
  "feeder.ok.rate": 1.000,
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
  "http.response.latency.millis.min": 0,
  "http.response.latency.millis.avg": 0,
  "http.response.latency.millis.max": 0,
  "http.response.code.counts": {
    "200": 1
  }
}
`, stdout.String())
}
