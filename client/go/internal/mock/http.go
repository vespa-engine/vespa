package mock

import (
	"bytes"
	"io"
	"net/http"
	"strconv"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

type HTTPClient struct {
	// The responses to return for future requests. Once a response is consumed, it's removed from this slice
	nextResponses []HTTPResponse

	// LastRequest is the last HTTP request made through this
	LastRequest *http.Request

	// Requests contains all requests made through this
	Requests []*http.Request
}

type HTTPResponse struct {
	Status int
	Body   []byte
	Header http.Header
}

func (c *HTTPClient) NextStatus(status int) { c.NextResponseBytes(status, nil) }

func (c *HTTPClient) NextResponseString(status int, body string) {
	c.NextResponseBytes(status, []byte(body))
}

func (c *HTTPClient) NextResponseBytes(status int, body []byte) {
	c.nextResponses = append(c.nextResponses, HTTPResponse{Status: status, Body: body})
}

func (c *HTTPClient) NextResponse(response HTTPResponse) {
	c.nextResponses = append(c.nextResponses, response)
}

func (c *HTTPClient) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	response := HTTPResponse{Status: 200}
	if len(c.nextResponses) > 0 {
		response = c.nextResponses[0]
		c.nextResponses = c.nextResponses[1:]
	}
	c.LastRequest = request
	c.Requests = append(c.Requests, request)
	if response.Header == nil {
		response.Header = make(http.Header)
	}
	return &http.Response{
			Status:     "Status " + strconv.Itoa(response.Status),
			StatusCode: response.Status,
			Body:       io.NopCloser(bytes.NewBuffer(response.Body)),
			Header:     response.Header,
		},
		nil
}

func (c *HTTPClient) Clone() util.HTTPClient { return c }
