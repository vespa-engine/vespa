package document

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/klauspost/compress/gzip"

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

type Compression int

const (
	CompressionAuto Compression = iota
	CompressionNone
	CompressionGzip
)

var (
	fieldsPrefix = []byte(`{"fields":`)
	fieldsSuffix = []byte("}")
)

// Client represents a HTTP client for the /document/v1/ API.
type Client struct {
	options     ClientOptions
	httpClients []countingHTTPClient
	now         func() time.Time
	sendCount   int32
	gzippers    sync.Pool
	buffers     sync.Pool
}

// ClientOptions specifices the configuration options of a feed client.
type ClientOptions struct {
	BaseURL     string
	Timeout     time.Duration
	Route       string
	TraceLevel  int
	Compression Compression
	Speedtest   bool
	NowFunc     func() time.Time
}

type countingHTTPClient struct {
	client   util.HTTPClient
	inflight int64
}

func (c *countingHTTPClient) addInflight(n int64) { atomic.AddInt64(&c.inflight, n) }

func (c *countingHTTPClient) Do(req *http.Request, timeout time.Duration) (*http.Response, error) {
	defer c.addInflight(-1)
	return c.client.Do(req, timeout)
}

func NewClient(options ClientOptions, httpClients []util.HTTPClient) (*Client, error) {
	if len(httpClients) < 1 {
		return nil, fmt.Errorf("need at least one HTTP client")
	}
	_, err := url.Parse(options.BaseURL)
	if err != nil {
		return nil, fmt.Errorf("invalid base url: %w", err)
	}
	countingClients := make([]countingHTTPClient, 0, len(httpClients))
	for _, client := range httpClients {
		countingClients = append(countingClients, countingHTTPClient{client: client})
	}
	nowFunc := options.NowFunc
	if nowFunc == nil {
		nowFunc = time.Now
	}
	c := &Client{
		options:     options,
		httpClients: countingClients,
		now:         nowFunc,
	}
	c.gzippers.New = func() any { return gzip.NewWriter(io.Discard) }
	c.buffers.New = func() any { return &bytes.Buffer{} }
	return c, nil
}

func writeQueryParam(sb *strings.Builder, start int, escape bool, k, v string) {
	if sb.Len() == start {
		sb.WriteString("?")
	} else {
		sb.WriteString("&")
	}
	sb.WriteString(k)
	sb.WriteString("=")
	if escape {
		sb.WriteString(url.QueryEscape(v))
	} else {
		sb.WriteString(v)
	}
}

func writeRequestBody(w io.Writer, body []byte) error {
	for _, b := range [][]byte{fieldsPrefix, body, fieldsSuffix} {
		if _, err := w.Write(b); err != nil {
			return err
		}
	}
	return nil
}

func (c *Client) methodAndURL(d Document) (string, string) {
	httpMethod := ""
	switch d.Operation {
	case OperationPut:
		httpMethod = "POST"
	case OperationUpdate:
		httpMethod = "PUT"
	case OperationRemove:
		httpMethod = "DELETE"
	}
	var sb strings.Builder
	// Base URL and path
	sb.WriteString(c.options.BaseURL)
	if !strings.HasSuffix(c.options.BaseURL, "/") {
		sb.WriteString("/")
	}
	sb.WriteString("document/v1/")
	sb.WriteString(url.PathEscape(d.Id.Namespace))
	sb.WriteString("/")
	sb.WriteString(url.PathEscape(d.Id.Type))
	if d.Id.Number != nil {
		sb.WriteString("/number/")
		n := uint64(*d.Id.Number)
		sb.WriteString(strconv.FormatUint(n, 10))
	} else if d.Id.Group != "" {
		sb.WriteString("/group/")
		sb.WriteString(url.PathEscape(d.Id.Group))
	} else {
		sb.WriteString("/docid")
	}
	sb.WriteString("/")
	sb.WriteString(url.PathEscape(d.Id.UserSpecific))
	// Query part
	queryStart := sb.Len()
	if c.options.Timeout > 0 {
		writeQueryParam(&sb, queryStart, false, "timeout", strconv.FormatInt(c.options.Timeout.Milliseconds(), 10)+"ms")
	}
	if c.options.Route != "" {
		writeQueryParam(&sb, queryStart, true, "route", c.options.Route)
	}
	if c.options.TraceLevel > 0 {
		writeQueryParam(&sb, queryStart, false, "tracelevel", strconv.Itoa(c.options.TraceLevel))
	}
	if c.options.Speedtest {
		writeQueryParam(&sb, queryStart, false, "dryRun", "true")
	}
	if d.Condition != "" {
		writeQueryParam(&sb, queryStart, true, "condition", d.Condition)
	}
	if d.Create {
		writeQueryParam(&sb, queryStart, false, "create", "true")
	}
	return httpMethod, sb.String()
}

func (c *Client) leastBusyClient() *countingHTTPClient {
	leastBusy := c.httpClients[0]
	min := int64(math.MaxInt64)
	next := atomic.AddInt32(&c.sendCount, 1)
	start := int(next) % len(c.httpClients)
	for i := range c.httpClients {
		j := (i + start) % len(c.httpClients)
		client := c.httpClients[j]
		inflight := atomic.LoadInt64(&client.inflight)
		if inflight < min {
			leastBusy = client
			min = inflight
		}
	}
	leastBusy.addInflight(1)
	return &leastBusy
}

func (c *Client) gzipWriter(w io.Writer) *gzip.Writer {
	gzipWriter := c.gzippers.Get().(*gzip.Writer)
	gzipWriter.Reset(w)
	return gzipWriter
}

func (c *Client) buffer() *bytes.Buffer {
	buf := c.buffers.Get().(*bytes.Buffer)
	buf.Reset()
	return buf
}

func (c *Client) createRequest(method, url string, body []byte) (*http.Request, error) {
	var buf *bytes.Buffer
	useGzip := c.options.Compression == CompressionGzip || (c.options.Compression == CompressionAuto && len(body) > 512)
	if useGzip {
		buf = bytes.NewBuffer(make([]byte, 0, 1024))
		w := c.gzipWriter(buf)
		writeRequestBody(w, body)
		if err := w.Close(); err != nil {
			return nil, err
		}
		c.gzippers.Put(w)
	} else {
		buf = bytes.NewBuffer(make([]byte, 0, len(fieldsPrefix)+len(body)+len(fieldsSuffix)))
		writeRequestBody(buf, body)
	}
	req, err := http.NewRequest(method, url, buf)
	if err != nil {
		return nil, err
	}
	if useGzip {
		req.Header.Set("Content-Encoding", "gzip")
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	req.ContentLength = int64(buf.Len())
	return req, nil
}

func (c *Client) clientTimeout() time.Duration {
	if c.options.Timeout < 1 {
		return 190 * time.Second
	}
	return c.options.Timeout*11/10 + 1000 // slightly higher than the server-side timeout
}

// Send given document to the endpoint configured in this client.
func (c *Client) Send(document Document) Result {
	start := c.now()
	result := Result{Id: document.Id, Stats: Stats{Requests: 1}}
	method, url := c.methodAndURL(document)
	req, err := c.createRequest(method, url, document.Fields)
	if err != nil {
		return resultWithErr(result, err)
	}
	resp, err := c.leastBusyClient().Do(req, c.clientTimeout())
	if err != nil {
		return resultWithErr(result, err)
	}
	defer resp.Body.Close()
	elapsed := c.now().Sub(start)
	return c.resultWithResponse(resp, req.ContentLength, result, elapsed)
}

func resultWithErr(result Result, err error) Result {
	result.Stats.Errors++
	result.Status = StatusTransportFailure
	result.Err = err
	return result
}

func (c *Client) resultWithResponse(resp *http.Response, sentBytes int64, result Result, elapsed time.Duration) Result {
	result.HTTPStatus = resp.StatusCode
	result.Stats.Responses++
	result.Stats.ResponsesByCode = map[int]int64{resp.StatusCode: 1}
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
	buf := c.buffer()
	defer c.buffers.Put(buf)
	written, err := io.Copy(buf, resp.Body)
	if err != nil {
		result.Status = StatusVespaFailure
		result.Err = err
	} else {
		if err := json.Unmarshal(buf.Bytes(), &body); err != nil {
			result.Status = StatusVespaFailure
			result.Err = fmt.Errorf("failed to decode json response: %w", err)
		}
	}
	result.Message = body.Message
	result.Trace = string(body.Trace)
	result.Stats.BytesSent = sentBytes
	result.Stats.BytesRecv = int64(written)
	if !result.Success() {
		result.Stats.Errors++
	}
	result.Stats.TotalLatency = elapsed
	result.Stats.MinLatency = elapsed
	result.Stats.MaxLatency = elapsed
	return result
}
