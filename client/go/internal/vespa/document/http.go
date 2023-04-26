package document

import (
	"bytes"
	"compress/gzip"
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

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

type Compression int

const (
	CompressionAuto Compression = iota
	CompressionNone
	CompressionGzip
)

// Client represents a HTTP client for the /document/v1/ API.
type Client struct {
	options     ClientOptions
	httpClients []countingHTTPClient
	now         func() time.Time
	sendCount   int32
	gzippers    sync.Pool
}

// ClientOptions specifices the configuration options of a feed client.
type ClientOptions struct {
	BaseURL     string
	Timeout     time.Duration
	Route       string
	TraceLevel  int
	Compression Compression
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

type countingReader struct {
	reader    io.Reader
	bytesRead int64
}

func (r *countingReader) Read(p []byte) (int, error) {
	n, err := r.reader.Read(p)
	r.bytesRead += int64(n)
	return n, err
}

func NewClient(options ClientOptions, httpClients []util.HTTPClient) *Client {
	if len(httpClients) < 1 {
		panic("need at least one HTTP client")
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
	return c
}

func (c *Client) queryParams() url.Values {
	params := url.Values{}
	timeout := c.options.Timeout
	if timeout == 0 {
		timeout = 200 * time.Second
	} else {
		timeout = timeout*11/10 + 1000
	}
	params.Set("timeout", strconv.FormatInt(timeout.Milliseconds(), 10)+"ms")
	if c.options.Route != "" {
		params.Set("route", c.options.Route)
	}
	if c.options.TraceLevel > 0 {
		params.Set("tracelevel", strconv.Itoa(c.options.TraceLevel))
	}
	return params
}

func urlPath(id Id) string {
	var sb strings.Builder
	sb.WriteString("/document/v1/")
	sb.WriteString(url.PathEscape(id.Namespace))
	sb.WriteString("/")
	sb.WriteString(url.PathEscape(id.Type))
	if id.Number != nil {
		sb.WriteString("/number/")
		n := uint64(*id.Number)
		sb.WriteString(strconv.FormatUint(n, 10))
	} else if id.Group != "" {
		sb.WriteString("/group/")
		sb.WriteString(url.PathEscape(id.Group))
	} else {
		sb.WriteString("/docid")
	}
	sb.WriteString("/")
	sb.WriteString(url.PathEscape(id.UserSpecific))
	return sb.String()
}

func (c *Client) feedURL(d Document, queryParams url.Values) (string, *url.URL, error) {
	u, err := url.Parse(c.options.BaseURL)
	if err != nil {
		return "", nil, fmt.Errorf("invalid base url: %w", err)
	}
	httpMethod := ""
	switch d.Operation {
	case OperationPut:
		httpMethod = "POST"
	case OperationUpdate:
		httpMethod = "PUT"
	case OperationRemove:
		httpMethod = "DELETE"
	}
	if d.Condition != "" {
		queryParams.Set("condition", d.Condition)
	}
	if d.Create {
		queryParams.Set("create", "true")
	}
	u.Path = urlPath(d.Id)
	u.RawQuery = queryParams.Encode()
	return httpMethod, u, nil
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

func (c *Client) createRequest(method, url string, body []byte) (*http.Request, error) {
	var r io.Reader
	useGzip := c.options.Compression == CompressionGzip || (c.options.Compression == CompressionAuto && len(body) > 512)
	if useGzip {
		var buf bytes.Buffer
		w := c.gzipWriter(&buf)
		if _, err := w.Write(body); err != nil {
			return nil, err
		}
		if err := w.Close(); err != nil {
			return nil, err
		}
		c.gzippers.Put(w)
		r = &buf
	} else {
		r = bytes.NewReader(body)
	}
	req, err := http.NewRequest(method, url, r)
	if err != nil {
		return nil, err
	}
	if useGzip {
		req.Header.Set("Content-Encoding", "gzip")
	}
	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	return req, nil
}

// Send given document to the endpoint configured in this client.
func (c *Client) Send(document Document) Result {
	start := c.now()
	result := Result{Id: document.Id, Stats: Stats{Requests: 1}}
	method, url, err := c.feedURL(document, c.queryParams())
	if err != nil {
		return resultWithErr(result, err)
	}
	req, err := c.createRequest(method, url.String(), document.Body)
	if err != nil {
		return resultWithErr(result, err)
	}
	resp, err := c.leastBusyClient().Do(req, 190*time.Second)
	if err != nil {
		return resultWithErr(result, err)
	}
	defer resp.Body.Close()
	elapsed := c.now().Sub(start)
	return resultWithResponse(resp, result, document, elapsed)
}

func resultWithErr(result Result, err error) Result {
	result.Stats.Errors++
	result.Status = StatusTransportFailure
	result.Err = err
	return result
}

func resultWithResponse(resp *http.Response, result Result, document Document, elapsed time.Duration) Result {
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
	cr := countingReader{reader: resp.Body}
	jsonDec := json.NewDecoder(&cr)
	if err := jsonDec.Decode(&body); err != nil {
		result.Status = StatusVespaFailure
		result.Err = fmt.Errorf("failed to decode json response: %w", err)
	}
	result.Message = body.Message
	result.Trace = string(body.Trace)
	result.Stats.BytesSent = int64(len(document.Body))
	result.Stats.BytesRecv = cr.bytesRead
	if !result.Success() {
		result.Stats.Errors++
	}
	result.Stats.TotalLatency = elapsed
	result.Stats.MinLatency = elapsed
	result.Stats.MaxLatency = elapsed
	return result
}
