package feed

import (
	"bytes"
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

// Result represents the result of a feeding operation
type Result struct{}

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
func (c *Client) Send(document Document) (Result, error) {
	method, url, err := document.FeedURL(c.options.BaseURL, c.queryParams())
	if err != nil {
		return Result{}, err
	}
	body := document.Body()
	req, err := http.NewRequest(method, url.String(), bytes.NewReader(body))
	if err != nil {
		return Result{}, err
	}
	resp, err := c.httpClient.Do(req, c.options.Timeout)
	if err != nil {
		return Result{}, err
	}
	defer resp.Body.Close()
	return Result{}, nil
}
