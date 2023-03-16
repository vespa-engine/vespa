package feed

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

// Client represents a HTTP client for the /document/v1/ API.
type Client struct {
	options    ClientOptions
	httpClient util.HTTPClient
}

// ClientOptions specifices the configuration options of a feed client.
type ClientOptions struct {
	BaseURL    string
	Timeout    time.Duration
	Route      string
	TraceLevel *int
}

func NewClient(options ClientOptions, httpClient util.HTTPClient) *Client {
	return &Client{options: options, httpClient: httpClient}
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
	method, url, err := document.FeedURL(c.options.BaseURL, c.queryParams())
	if err != nil {
		return Result{Status: StatusError, Err: err}
	}
	body := document.Body()
	req, err := http.NewRequest(method, url.String(), bytes.NewReader(body))
	if err != nil {
		return Result{Status: StatusError, Err: err}
	}
	resp, err := c.httpClient.Do(req, c.options.Timeout)
	if err != nil {
		return Result{Status: StatusTransportFailure, Err: err}
	}
	defer resp.Body.Close()
	return createResult(resp)
}

func createResult(resp *http.Response) Result {
	result := Result{}
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
	jsonDec := json.NewDecoder(resp.Body)
	if err := jsonDec.Decode(&body); err != nil {
		result.Status = StatusError
		result.Err = fmt.Errorf("failed to decode json response: %w", err)
		return result
	}
	result.Message = body.Message
	result.Trace = string(body.Trace)
	return result
}
