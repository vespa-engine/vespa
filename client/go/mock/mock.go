package mock

import (
	"bytes"
	"crypto/tls"
	"io/ioutil"
	"net/http"
	"strconv"
	"time"
)

type HTTPClient struct {
	// The responses to return for future requests. Once a response is consumed, it's removed from this slice
	nextResponses []httpResponse

	// LastRequest is the last HTTP request made through this
	LastRequest *http.Request

	// Requests contains all requests made through this
	Requests []*http.Request
}

type httpResponse struct {
	status int
	body   []byte
}

func (c *HTTPClient) NextStatus(status int) { c.NextResponseBytes(status, nil) }

func (c *HTTPClient) NextResponse(status int, body string) {
	c.NextResponseBytes(status, []byte(body))
}

func (c *HTTPClient) NextResponseBytes(status int, body []byte) {
	c.nextResponses = append(c.nextResponses, httpResponse{status: status, body: body})
}

func (c *HTTPClient) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	response := httpResponse{status: 200}
	if len(c.nextResponses) > 0 {
		response = c.nextResponses[0]
		c.nextResponses = c.nextResponses[1:]
	}
	c.LastRequest = request
	c.Requests = append(c.Requests, request)
	return &http.Response{
			Status:     "Status " + strconv.Itoa(response.status),
			StatusCode: response.status,
			Body:       ioutil.NopCloser(bytes.NewBuffer(response.body)),
			Header:     make(http.Header),
		},
		nil
}

func (c *HTTPClient) UseCertificate(certificates []tls.Certificate) {}
