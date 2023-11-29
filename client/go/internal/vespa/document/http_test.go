// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package document

import (
	"bytes"
	"fmt"
	"net/http"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

type manualClock struct {
	t    time.Time
	tick time.Duration
}

func (c *manualClock) now() time.Time {
	c.advance(c.tick)
	return c.t
}

func (c *manualClock) advance(d time.Duration) { c.t = c.t.Add(d) }

type mockHTTPClient struct {
	id int
	*mock.HTTPClient
}

func TestLeastBusyClient(t *testing.T) {
	httpClient := mock.HTTPClient{}
	var httpClients []httputil.Client
	for i := 0; i < 4; i++ {
		httpClients = append(httpClients, &mockHTTPClient{i, &httpClient})
	}
	client, _ := NewClient(ClientOptions{}, httpClients)
	client.httpClients[0].inflight.Add(1)
	client.httpClients[1].inflight.Add(1)
	assertLeastBusy(t, 2, client)
	assertLeastBusy(t, 3, client)
	assertLeastBusy(t, 3, client)
	client.httpClients[3].inflight.Add(1)
	client.httpClients[1].inflight.Add(-1)
	assertLeastBusy(t, 1, client)
}

func assertLeastBusy(t *testing.T, id int, client *Client) {
	t.Helper()
	leastBusy := client.leastBusyClient()
	got := leastBusy.client.(*mockHTTPClient).id
	if got != id {
		t.Errorf("got client.id=%d, want %d", got, id)
	}
}

func TestClientSend(t *testing.T) {
	var tests = []struct {
		in     Document
		method string
		url    string
	}{
		{Document{Create: true, Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "123"}}`)},
			"PUT",
			"https://example.com:1337/document/v1/ns/type/docid/doc1?timeout=5000ms&create=true"},
		{Document{Id: mustParseId("id:ns:type::doc2"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "456"}}`)},
			"PUT",
			"https://example.com:1337/document/v1/ns/type/docid/doc2?timeout=5000ms"},
		{Document{Id: mustParseId("id:ns:type::doc3"), Operation: OperationRemove},
			"DELETE",
			"https://example.com:1337/document/v1/ns/type/docid/doc3?timeout=5000ms"},
		{Document{Condition: "foo", Id: mustParseId("id:ns:type::doc4"), Operation: OperationUpdate, Body: []byte(`{"fields":{"baz": "789"}}`)},
			"PUT",
			"https://example.com:1337/document/v1/ns/type/docid/doc4?timeout=5000ms&condition=foo"},
		{Document{Id: mustParseId("id:ns:type::doc5"), Operation: OperationPut, Body: []byte(`{"fields":{"baz": "789"}}`)},
			"POST",
			"https://example.com:1337/document/v1/ns/type/docid/doc5?timeout=5000ms"},
	}
	httpClient := mock.HTTPClient{ReadBody: true}
	client, _ := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, []httputil.Client{&httpClient})
	clock := manualClock{t: time.Now(), tick: time.Second}
	client.now = clock.now
	var stats Stats
	for i, tt := range tests {
		doc := tt.in
		wantRes := Result{
			Id:      doc.Id,
			Latency: time.Second,
		}
		switch i {
		case 0, 1, 2:
			msg := `{"message":"All good!"}`
			httpClient.NextResponseString(200, msg)
			wantRes.Status = StatusSuccess
			wantRes.HTTPStatus = 200
			wantRes.BytesRecv = 23
		case 3:
			errMsg := `something went wront`
			httpClient.NextResponseString(502, errMsg)
			wantRes.Status = StatusVespaFailure
			wantRes.HTTPStatus = 502
			wantRes.Body = []byte(errMsg)
			wantRes.BytesRecv = 20
		case 4:
			transportErr := fmt.Errorf("transport error")
			httpClient.NextResponseError(transportErr)
			wantRes.Err = transportErr
			wantRes.Status = StatusTransportFailure
		}
		res := client.Send(doc)
		if res.Err == nil {
			wantRes.BytesSent = int64(len(httpClient.LastBody))
		}
		if !reflect.DeepEqual(res, wantRes) {
			t.Fatalf("#%d: got result %+v, want %+v", i, res, wantRes)
		}
		stats.Add(res, false)
		r := httpClient.LastRequest
		if r.Method != tt.method {
			t.Errorf("#%d: got r.Method = %q, want %q", i, r.Method, tt.method)
		}
		var headers http.Header = map[string][]string{
			"Content-Type": {"application/json; charset=utf-8"},
		}
		if !reflect.DeepEqual(r.Header, headers) {
			t.Errorf("#%d: got r.Header = %v, want %v", i, r.Header, headers)
		}
		if r.URL.String() != tt.url {
			t.Errorf("#%d: got r.URL = %q, want %q", i, r.URL, tt.url)
		}
		if !bytes.Equal(httpClient.LastBody, doc.Body) {
			t.Errorf("#%d: got r.Body = %q, want %q", i, string(httpClient.LastBody), doc.Body)
		}
	}
	want := Stats{
		Operations: 5,
		Requests:   5,
		Responses:  4,
		ResponsesByCode: map[int]int64{
			200: 3,
			502: 1,
		},
		Errors:       1,
		Inflight:     0,
		TotalLatency: 5 * time.Second,
		MinLatency:   time.Second,
		MaxLatency:   time.Second,
		BytesSent:    75,
		BytesRecv:    89,
	}
	if !reflect.DeepEqual(want, stats) {
		t.Errorf("got %+v, want %+v", stats, want)
	}
}

func TestClientGet(t *testing.T) {
	httpClient := mock.HTTPClient{ReadBody: true}
	client, _ := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, []httputil.Client{&httpClient})
	clock := manualClock{t: time.Now(), tick: time.Second}
	client.now = clock.now
	doc := `{
    "pathId": "/document/v1/mynamespace/music/docid/doc1",
    "id": "id:mynamespace:music::doc1",
    "fields": {
        "artist": "Metallica",
        "album": "Master of Puppets"
    }
}`
	id := Id{Namespace: "mynamespace", Type: "music", UserSpecific: "doc1"}
	httpClient.NextResponseString(200, doc)
	result := client.Get(id)
	want := Result{
		Id:         id,
		Body:       []byte(doc),
		Status:     StatusSuccess,
		HTTPStatus: 200,
		Latency:    time.Second,
		BytesRecv:  192,
	}
	if !reflect.DeepEqual(want, result) {
		t.Errorf("got %+v, want %+v", result, want)
	}
}

func TestClientSendCompressed(t *testing.T) {
	httpClient := &mock.HTTPClient{ReadBody: true}
	client, _ := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, []httputil.Client{httpClient})

	bigBody := fmt.Sprintf(`{"fields": {"foo": "%s"}}`, strings.Repeat("s", 512+1))
	bigDoc := Document{Create: true, Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(bigBody)}
	smallDoc := Document{Create: true, Id: mustParseId("id:ns:type::doc2"), Operation: OperationUpdate, Body: []byte(`{"fields": {"foo": "s"}}`)}

	var result Result
	client.options.Compression = CompressionNone
	result = client.Send(bigDoc)
	assertCompressedRequest(t, false, result, httpClient)
	result = client.Send(smallDoc)
	assertCompressedRequest(t, false, result, httpClient)

	client.options.Compression = CompressionAuto
	result = client.Send(bigDoc)
	assertCompressedRequest(t, true, result, httpClient)
	result = client.Send(smallDoc)
	assertCompressedRequest(t, false, result, httpClient)

	client.options.Compression = CompressionGzip
	result = client.Send(bigDoc)
	assertCompressedRequest(t, true, result, httpClient)
	result = client.Send(smallDoc)
	assertCompressedRequest(t, true, result, httpClient)
}

func assertCompressedRequest(t *testing.T, want bool, result Result, client *mock.HTTPClient) {
	t.Helper()
	wantEnc := ""
	if want {
		wantEnc = "gzip"
	}
	gotEnc := client.LastRequest.Header.Get("Content-Encoding")
	if gotEnc != wantEnc {
		t.Errorf("got Content-Encoding=%q, want %q", gotEnc, wantEnc)
	}
	if result.BytesSent != int64(len(client.LastBody)) {
		t.Errorf("got BytesSent=%d, want %d", result.BytesSent, len(client.LastBody))
	}
	compressed := bytes.HasPrefix(client.LastBody, []byte{0x1f, 0x8b})
	if compressed != want {
		t.Errorf("got compressed=%t, want %t", compressed, want)
	}
}

func TestClientMethodAndURL(t *testing.T) {
	tests := []struct {
		in      Document
		options ClientOptions
		method  string
		url     string
	}{
		{
			Document{
				Id: mustParseId("id:ns:type:n=123:user"),
			},
			ClientOptions{},
			"POST",
			"https://example.com/document/v1/ns/type/number/123/user",
		},
		{
			Document{
				Id: mustParseId("id:ns:type:g=foo:user"),
			},
			ClientOptions{},
			"POST",
			"https://example.com/document/v1/ns/type/group/foo/user",
		},
		{
			Document{
				Id: mustParseId("id:ns:type::user::specific"),
			},
			ClientOptions{},
			"POST",
			"https://example.com/document/v1/ns/type/docid/user::specific",
		},
		{
			Document{
				Id: mustParseId("id:ns:type:::"),
			},
			ClientOptions{Route: "elsewhere"},
			"POST",
			"https://example.com/document/v1/ns/type/docid/:?route=elsewhere",
		},
		{
			Document{
				Id:        mustParseId("id:ns:type-with-/::user"),
				Condition: "foo/bar",
			},
			ClientOptions{},
			"POST",
			"https://example.com/document/v1/ns/type-with-%2F/docid/user?condition=foo%2Fbar",
		},
		{
			Document{
				Id:        mustParseId("id:ns:type::user"),
				Operation: OperationUpdate,
				Create:    true,
				Condition: "false",
			},
			ClientOptions{Timeout: 10 * time.Second, TraceLevel: 5, Speedtest: true},
			"PUT",
			"https://example.com/document/v1/ns/type/docid/user?timeout=10000ms&tracelevel=5&dryRun=true&condition=false&create=true",
		},
		{
			Document{
				Id:        mustParseId("id:ns:type::user"),
				Operation: OperationRemove,
			},
			ClientOptions{},
			"DELETE",
			"https://example.com/document/v1/ns/type/docid/user",
		},
	}
	httpClient := mock.HTTPClient{}
	client, _ := NewClient(ClientOptions{
		BaseURL: "https://example.com/",
	}, []httputil.Client{&httpClient})
	for i, tt := range tests {
		client.options.Timeout = tt.options.Timeout
		client.options.Route = tt.options.Route
		client.options.TraceLevel = tt.options.TraceLevel
		client.options.Speedtest = tt.options.Speedtest
		method, url := client.methodAndURL(tt.in, &bytes.Buffer{})
		if url != tt.url || method != tt.method {
			t.Errorf("#%d: methodAndURL(doc) = (%s, %s), want (%s, %s)", i, method, url, tt.method, tt.url)
		}
	}
}

func benchmarkClientSend(b *testing.B, compression Compression, document Document) {
	b.Helper()
	httpClient := mock.HTTPClient{}
	client, _ := NewClient(ClientOptions{
		Compression: compression,
		BaseURL:     "https://example.com:1337",
		Timeout:     time.Duration(5 * time.Second),
	}, []httputil.Client{&httpClient})
	b.ResetTimer() // ignore setup
	for n := 0; n < b.N; n++ {
		client.Send(document)
	}
}

func makeDocument(size int) Document {
	return Document{Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(fmt.Sprintf(`{"fields": {"foo": "%s"}}`, randString(size)))}
}

func BenchmarkClientSendSmallUncompressed(b *testing.B) {
	benchmarkClientSend(b, CompressionNone, makeDocument(10))
}

func BenchmarkClientSendMediumUncompressed(b *testing.B) {
	benchmarkClientSend(b, CompressionNone, makeDocument(1000))
}

func BenchmarkClientSendMediumGzip(b *testing.B) {
	benchmarkClientSend(b, CompressionGzip, makeDocument(1000))
}
