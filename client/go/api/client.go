package vespa

import (
	"bytes"
	"compress/gzip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"sync"
)

const (
	// DefaultHost is the default host where Vespa would be running. All endpoints should be relative to this url
	DefaultHost = "127.0.0.1:6800"
	// DefaultScheme is the default schema used to communicate with Vespa.
	DefaultScheme = "http"
	// DefaultContentType is the default time we expect
	DefaultContentType = "application/json"
)

// Doer is the interface that wraps the Do methos which performs an http Request.
type Doer interface {
	Do(*http.Request) (*http.Response, error)
}

// ClientOptionFunc is a function that handles a single configure option for
// a Client. It is used when calling NewClient
type ClientOptionFunc func(*Client) error

// Client is the Vespa Client. Usually created by calling NewClient
type Client struct {
	c           Doer
	baseURL     *url.URL
	UserAgent   string
	GzipRequest bool
}

// NewClient creates a new client to work with vespa. If no options provided, it
// will create the options with default values
func NewClient(ctx context.Context, options ...ClientOptionFunc) (*Client, error) {

	c := &Client{
		c: http.DefaultClient,
	}

	c.baseURL = &url.URL{Host: DefaultHost, Scheme: DefaultScheme}

	// Run the options on it
	for _, option := range options {
		if err := option(c); err != nil {
			return nil, err
		}
	}

	return c, nil
}

// SetHTTPClient can be used to specify the http.Client to use when making
// HTTP requests to Elasticsearch.
func SetHTTPClient(httpClient Doer) ClientOptionFunc {
	return func(c *Client) error {
		if httpClient != nil {
			c.c = httpClient
		} else {
			c.c = http.DefaultClient
		}
		return nil
	}
}

// SetURL defines the URL where Vespa can be reached.
func SetURL(urlStr string) ClientOptionFunc {
	return func(c *Client) error {
		u, err := url.Parse(urlStr)
		if err != nil {
			return err
		}

		c.baseURL = u
		return nil
	}
}

// GzipRequests will make all requests' body be gzipped before sending
func GzipRequests(v bool) ClientOptionFunc {
	return func(c *Client) error {
		c.GzipRequest = v
		return nil
	}
}

// PerformRequest contains all the necessary parameters in order to
// performn a Request to Vespa.
type PerformRequest struct {
	Method      string
	Path        string
	Params      url.Values
	Body        interface{}
	ContentType string
	// IgnoreErrors     []int
	// // Retrier          Retrier
	// RetryStatusCodes []int
	Headers         http.Header
	MaxResponseSize int64
}

// PerformRequest will send the request to Vespa and will return a http.Response object. In
// the case of an error, it will return
func (c *Client) PerformRequest(ctx context.Context, opt PerformRequest) (*http.Response, error) {
	pathWithParams := opt.Path
	if len(opt.Params) > 0 {
		pathWithParams = fmt.Sprintf("%s?%s", pathWithParams, opt.Params.Encode())
	}

	req, err := c.newRequest(opt.Method, pathWithParams, opt.Body)
	if err != nil {
		return nil, err
	}

	if opt.ContentType != "" {
		req.Header.Set("Content-Type", opt.ContentType)
	} else {
		req.Header.Set("Content-Type", DefaultContentType)
	}

	if len(opt.Headers) > 0 {
		for key, value := range opt.Headers {
			for _, v := range value {
				req.Header.Add(key, v)
			}
		}
	}

	res, err := c.c.Do(req.WithContext(ctx))
	if err != nil {
		return nil, err
	}

	return res, nil

}

func (c *Client) newRequest(method, path string, body interface{}) (*http.Request, error) {
	var req *http.Request

	rel, err := url.Parse(path)
	if err != nil {
		return nil, err
	}

	u := c.baseURL.ResolveReference(rel)

	var buf io.ReadWriter
	if body != nil {
		if c.GzipRequest {
			r, w := io.Pipe()

			go func() {
				gz := GetGzipWriter()
				gz.Reset(w)
				defer gzPool.Put(gz)

				if err := json.NewEncoder(gz).Encode(body); err != nil {
					log.Println(err)
				}

				err = gz.Close()
				if err != nil {
					log.Println(err)
				}

				err = w.CloseWithError(err)
				if err != nil {
					log.Println(err)
				}
			}()

			req, err = http.NewRequest(method, u.String(), r)
			if err != nil {
				return nil, err
			}
			req.Header.Set("Content-Encoding", "gzip")
		} else {
			buf = new(bytes.Buffer)
			err := json.NewEncoder(buf).Encode(body)
			if err != nil {
				return nil, err
			}
			req, err = http.NewRequest(method, u.String(), buf)
			if err != nil {
				return nil, err
			}
		}
	} else {
		req, err = http.NewRequest(method, u.String(), nil)
		if err != nil {
			return nil, err
		}
	}

	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", c.UserAgent)

	return req, nil
}

var gzPool = sync.Pool{
	New: func() interface{} {
		return gzip.NewWriter(ioutil.Discard)
	},
}

// GetGzipWriter grabs a gzipper from the sync pool
func GetGzipWriter() *gzip.Writer {
	return gzPool.Get().(*gzip.Writer)
}

// PutGzipWriter returns the gzipper to the sync pool
func PutGzipWriter(buf *gzip.Writer) {
	err := buf.Flush()
	if err != nil {
		log.Printf("Could not flush writer: %v", err)
	}
	gzPool.Put(buf)
}
