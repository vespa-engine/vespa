package feed

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"sync"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

// Client represents a HTTP client for the /document/v1/ API.
type Client struct {
	options    ClientOptions
	httpClient util.HTTPClient
	stats      Stats
	mu         sync.Mutex
	now        func() time.Time
}

// ClientOptions specifices the configuration options of a feed client.
type ClientOptions struct {
	BaseURL    string
	Timeout    time.Duration
	Route      string
	TraceLevel *int
}

type countingReader struct {
	reader    io.Reader
	bytesRead int64
}

func (r *countingReader) Read(p []byte) (int, error) {
	n, err := r.reader.Read(p)
	r.bytesRead += int64(n)
	return n, err
}

func NewClient(options ClientOptions, httpClient util.HTTPClient) *Client {
	return &Client{
		options:    options,
		httpClient: httpClient,
		stats:      NewStats(),
		now:        time.Now,
	}
}

func (c *Client) queryParams() url.Values {
	params := url.Values{}
	if c.options.Timeout > 0 {
		params.Set("timeout", strconv.FormatInt(c.options.Timeout.Milliseconds(), 10)+"ms")
	}
	if c.options.Route != "" {
		params.Set("route", c.options.Route)
	}
	if c.options.TraceLevel != nil {
		params.Set("tracelevel", strconv.Itoa(*c.options.TraceLevel))
	}
	return params
}

// Send given document the URL configured in this client.
func (c *Client) Send(document Document) Result {
	start := c.now()
	stats := NewStats()
	stats.Requests = 1
	defer func() {
		latency := c.now().Sub(start)
		stats.TotalLatency = latency
		stats.MinLatency = latency
		stats.MaxLatency = latency
		c.addStats(&stats)
	}()
	method, url, err := document.FeedURL(c.options.BaseURL, c.queryParams())
	if err != nil {
		stats.Errors = 1
		return Result{Status: StatusError, Err: err}
	}
	body := document.Body()
	req, err := http.NewRequest(method, url.String(), bytes.NewReader(body))
	if err != nil {
		stats.Errors = 1
		return Result{Status: StatusError, Err: err}
	}
	resp, err := c.httpClient.Do(req, c.options.Timeout)
	if err != nil {
		stats.Errors = 1
		return Result{Status: StatusTransportFailure, Err: err}
	}
	defer resp.Body.Close()
	stats.Responses = 1
	stats.ResponsesByCode = map[int]int64{
		resp.StatusCode: 1,
	}
	stats.BytesSent = int64(len(body))
	return c.createResult(document.Id, &stats, resp)
}

func (c *Client) Stats() Stats { return c.stats }

func (c *Client) addStats(stats *Stats) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.stats.Add(*stats)
}

func (c *Client) createResult(id DocumentId, stats *Stats, resp *http.Response) Result {
	result := Result{Id: id}
	switch resp.StatusCode {
	case 200:
		result.Status = StatusSuccess
	case 412:
		result.Status = StatusConditionNotMet
	case 502, 504, 507:
		result.Status = StatusVespaFailure
	default:
		result.Status = StatusTransportFailure
	}
	var body struct {
		Message string          `json:"message"`
		Trace   json.RawMessage `json:"trace"`
	}
	cr := countingReader{reader: resp.Body}
	jsonDec := json.NewDecoder(&cr)
	if err := jsonDec.Decode(&body); err != nil {
		result.Status = StatusError
		result.Err = fmt.Errorf("failed to decode json response: %w", err)
	}
	result.Message = body.Message
	result.Trace = string(body.Trace)
	stats.BytesRecv = cr.bytesRead
	if !result.Status.Success() {
		stats.Errors = 1
	}
	return result
}
