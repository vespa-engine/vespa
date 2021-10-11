package vespa

import (
	"bytes"
	"compress/gzip"
	"context"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
)

func TestClientDefaults(t *testing.T) {
	ctx := context.Background()

	client, err := NewClient(ctx)
	if err != nil {
		t.Fatal(err)
	}

	durl := &url.URL{Scheme: "http", Host: "127.0.0.1:6800"}

	if client.baseURL.String() != durl.String() {
		t.Errorf("expected default url to be %v, got: %v", durl, client.baseURL)
	}

	if client.c != http.DefaultClient {
		t.Errorf("expected c to be %v, got: %v", http.DefaultClient, client.c)

	}
}

func TestClientWithErrorOption(t *testing.T) {
	ctx := context.Background()

	errFn := func(*Client) error {
		return errors.New("error")
	}

	_, err := NewClient(ctx, errFn)
	if err == nil {
		t.Errorf("expected New client to return error, got: %v", err)
	}
}

func TestClientWithHTTPClient(t *testing.T) {
	ctx := context.Background()

	c := &http.Client{}
	client, err := NewClient(ctx, SetHTTPClient(c))
	if err != nil {
		t.Fatal(err)
	}

	if client.c != c {
		t.Errorf("expected client.c(doer) to be %v, got: %v ", c, client.c)
	}
}

func TestClientWithNikHTTPClient(t *testing.T) {
	ctx := context.Background()

	client, err := NewClient(ctx, SetHTTPClient(nil))
	if err != nil {
		t.Fatal(err)
	}

	if client.c != http.DefaultClient {
		t.Errorf("expected client.c(doer) to be %v, got: %v ", http.DefaultClient, client.c)
	}
}

func TestClientWithSetURL(t *testing.T) {
	ctx := context.Background()

	client, err := NewClient(ctx, SetURL("https://localhost:8080"))
	if err != nil {
		t.Fatal(err)
	}

	expected := &url.URL{Host: "localhost:8080", Scheme: "https"}

	if client.baseURL.String() != expected.String() {
		t.Errorf("expected baseURL to be %v, got: %v", expected.String(), client.baseURL)
	}
}

func TestClientWithInvalidSetURL(t *testing.T) {
	ctx := context.Background()

	_, err := NewClient(ctx, SetURL("http://foo.com/?foo\nbar"))
	if err == nil {
		t.Errorf("expected NewClient() to return error, got: %v", err)
	}

}

func TestPerformRequest(t *testing.T) {

	data := `{"data": "json"})`
	ts := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			fmt.Fprint(w, data)
		},
	))

	ctx := context.Background()

	client, err := NewClient(ctx, SetURL(ts.URL))
	if err != nil {
		t.Fatal(err)
	}

	res, err := client.PerformRequest(ctx, PerformRequest{
		Method: "Get",
		Path:   "",
	})
	if err != nil {
		t.Fatal(err)
	}

	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		t.Fatal(err)
	}

	if string(body) != data {
		t.Errorf(" expected response body to return %s, got: %s", data, body)
	}

}

func TestPerformGzippedRequest(t *testing.T) {

	expect := "\"{\\\"type\\\": \\\"gzip\\\"}\"\n"
	ts := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, req *http.Request) {
			res, _ := ioutil.ReadAll(req.Body)
			b := bytes.NewBuffer(res)

			var r io.Reader
			r, err := gzip.NewReader(b)
			if err != nil {
				return
			}

			var resB bytes.Buffer
			_, err = resB.ReadFrom(r)
			if err != nil {
				return
			}

			resData := resB.String()

			fmt.Fprint(w, resData)
		},
	))

	ctx := context.Background()

	client, err := NewClient(ctx, SetURL(ts.URL), GzipRequests(true))
	if err != nil {
		t.Fatal(err)
	}

	res, err := client.PerformRequest(ctx, PerformRequest{
		Method: "POST",
		Path:   "",
		Body:   "{\"type\": \"gzip\"}",
	})
	if err != nil {
		t.Fatal(err)
	}

	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		t.Fatal(err)
	}

	fmt.Println(string(body))

	if string(body) != expect {
		t.Errorf(" expected response body to return %s, got: %s", expect, body)
	}

}

func TestPerformRequestWithParam(t *testing.T) {
	expect := `/document/v1?page=1`
	ts := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			data := fmt.Sprintf("%s?%s", r.URL.Path, r.URL.RawQuery)

			fmt.Fprint(w, data)
		},
	))

	ctx := context.Background()
	client, err := NewClient(ctx, SetURL(ts.URL))
	if err != nil {
		t.Fatal(err)
	}

	u := url.URL{}
	params := u.Query()
	params.Add("page", "1")

	res, err := client.PerformRequest(ctx, PerformRequest{
		Method: "Get",
		Path:   "document/v1",
		Params: params,
	})
	if err != nil {
		t.Fatal(err)
	}

	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		t.Fatal(err)
	}

	if string(body) != expect {
		t.Errorf(" expected response body to return %s, got: %s", expect, body)
	}

}

func TestPerformRequestWithContentHeader(t *testing.T) {
	expect := `Content-Type: application/json`
	ts := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			ct := r.Header.Get("Content-Type")
			data := fmt.Sprintf("Content-Type: %s", ct)
			fmt.Fprint(w, data)
		},
	))

	ctx := context.Background()
	client, err := NewClient(ctx, SetURL(ts.URL))
	if err != nil {
		t.Fatal(err)
	}

	res, err := client.PerformRequest(ctx, PerformRequest{
		Method:      "Get",
		Path:        "document/v1",
		ContentType: "application/json",
	})
	if err != nil {
		t.Fatal(err)
	}

	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		t.Fatal(err)
	}

	if string(body) != expect {
		t.Errorf(" expected response body to return %s, got: %s", expect, body)
	}
}

func TestPerformRequestWithHeader(t *testing.T) {
	expect := `header: value`
	ts := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			value := r.Header.Get("header")
			data := fmt.Sprintf("header: %s", value)
			fmt.Fprint(w, data)
		},
	))

	ctx := context.Background()
	client, err := NewClient(ctx, SetURL(ts.URL))
	if err != nil {
		t.Fatal(err)
	}

	headers := http.Header{}
	headers.Add("header", "value")

	res, err := client.PerformRequest(ctx, PerformRequest{
		Method:  "Get",
		Path:    "document/v1",
		Headers: headers,
	})
	if err != nil {
		t.Fatal(err)
	}

	body, err := ioutil.ReadAll(res.Body)
	if err != nil {
		t.Fatal(err)
	}

	if string(body) != expect {
		t.Errorf(" expected response body to return %s, got: %s", expect, body)
	}
}

func TestPerformRequestWithInvalidBody(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			value := r.Header.Get("header")
			data := fmt.Sprintf("header: %s", value)
			fmt.Fprint(w, data)
		},
	))

	ctx := context.Background()
	client, err := NewClient(ctx, SetURL(ts.URL))
	if err != nil {
		t.Fatal(err)
	}

	headers := http.Header{}
	headers.Add("header", "value")

	_, err = client.PerformRequest(ctx, PerformRequest{
		Method:  "Get",
		Path:    "document/v1",
		Headers: headers,
		Body:    make(chan int),
	})
	if err == nil {
		t.Errorf(" expected PerformRequest() to return error on invalid body value")
	}
}

func TestPerformRequestWithValidBody(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			value := r.Header.Get("header")
			data := fmt.Sprintf("header: %s", value)
			fmt.Fprint(w, data)
		},
	))

	ctx := context.Background()
	client, err := NewClient(ctx, SetURL(ts.URL))
	if err != nil {
		t.Fatal(err)
	}

	headers := http.Header{}
	headers.Add("header", "value")

	_, err = client.PerformRequest(ctx, PerformRequest{
		Method:  "Post",
		Path:    "document/v1",
		Headers: headers,
		Body:    `{"name": "valid"}`,
	})
	if err != nil {
		t.Errorf(" expected PerformRequest() to not return err")
	}
}
