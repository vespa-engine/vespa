package document

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/util"
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
	var httpClients []util.HTTPClient
	for i := 0; i < 4; i++ {
		httpClients = append(httpClients, &mockHTTPClient{i, &httpClient})
	}
	client := NewClient(ClientOptions{}, httpClients)
	client.httpClients[0].addInflight(1)
	client.httpClients[1].addInflight(1)
	assertLeastBusy(t, 2, client)
	assertLeastBusy(t, 2, client)
	assertLeastBusy(t, 3, client)
	client.httpClients[3].addInflight(1)
	client.httpClients[1].addInflight(-1)
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
	docs := []Document{
		{Create: true, Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "123"}}`)},
		{Create: true, Id: mustParseId("id:ns:type::doc2"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "456"}}`)},
		{Create: true, Id: mustParseId("id:ns:type::doc3"), Operation: OperationUpdate, Body: []byte(`{"fields":{"baz": "789"}}`)},
	}
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, []util.HTTPClient{&httpClient})
	clock := manualClock{t: time.Now(), tick: time.Second}
	client.now = clock.now
	var stats Stats
	for i, doc := range docs {
		wantRes := Result{
			Id: doc.Id,
			Stats: Stats{
				Requests:     1,
				Responses:    1,
				TotalLatency: time.Second,
				MinLatency:   time.Second,
				MaxLatency:   time.Second,
				BytesSent:    25,
			},
		}
		if i < 2 {
			httpClient.NextResponseString(200, `{"message":"All good!"}`)
			wantRes.Status = StatusSuccess
			wantRes.HTTPStatus = 200
			wantRes.Message = "All good!"
			wantRes.Stats.ResponsesByCode = map[int]int64{200: 1}
			wantRes.Stats.BytesRecv = 23
		} else {
			httpClient.NextResponseString(502, `{"message":"Good bye, cruel world!"}`)
			wantRes.Status = StatusVespaFailure
			wantRes.HTTPStatus = 502
			wantRes.Message = "Good bye, cruel world!"
			wantRes.Stats.ResponsesByCode = map[int]int64{502: 1}
			wantRes.Stats.Errors = 1
			wantRes.Stats.BytesRecv = 36
		}
		res := client.Send(doc)
		if !reflect.DeepEqual(res, wantRes) {
			t.Fatalf("got result %+v, want %+v", res, wantRes)
		}
		stats.Add(res.Stats)
		r := httpClient.LastRequest
		if r.Method != http.MethodPut {
			t.Errorf("got r.Method = %q, want %q", r.Method, http.MethodPut)
		}
		wantURL := fmt.Sprintf("https://example.com:1337/document/v1/ns/type/docid/%s?create=true&timeout=5500ms", doc.Id.UserSpecific)
		if r.URL.String() != wantURL {
			t.Errorf("got r.URL = %q, want %q", r.URL, wantURL)
		}
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Fatalf("got unexpected error %q", err)
		}
		wantBody := doc.Body
		if !bytes.Equal(body, wantBody) {
			t.Errorf("got r.Body = %q, want %q", string(body), string(wantBody))
		}
	}
	want := Stats{
		Requests:  3,
		Responses: 3,
		ResponsesByCode: map[int]int64{
			200: 2,
			502: 1,
		},
		Errors:       1,
		Inflight:     0,
		TotalLatency: 3 * time.Second,
		MinLatency:   time.Second,
		MaxLatency:   time.Second,
		BytesSent:    75,
		BytesRecv:    82,
	}
	if !reflect.DeepEqual(want, stats) {
		t.Errorf("got %+v, want %+v", stats, want)
	}
}

func TestClientSendCompressed(t *testing.T) {
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, []util.HTTPClient{&httpClient})

	bigBody := fmt.Sprintf(`{"fields":{"foo": "%s"}}`, strings.Repeat("s", 512+1))
	bigDoc := Document{Create: true, Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(bigBody)}
	smallDoc := Document{Create: true, Id: mustParseId("id:ns:type::doc2"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "s"}}`)}

	client.options.Compression = CompressionNone
	_ = client.Send(bigDoc)
	assertCompressedRequest(t, false, httpClient.LastRequest)
	_ = client.Send(smallDoc)
	assertCompressedRequest(t, false, httpClient.LastRequest)

	client.options.Compression = CompressionAuto
	_ = client.Send(bigDoc)
	assertCompressedRequest(t, true, httpClient.LastRequest)
	_ = client.Send(smallDoc)
	assertCompressedRequest(t, false, httpClient.LastRequest)

	client.options.Compression = CompressionGzip
	_ = client.Send(bigDoc)
	assertCompressedRequest(t, true, httpClient.LastRequest)
	_ = client.Send(smallDoc)
	assertCompressedRequest(t, true, httpClient.LastRequest)
}

func assertCompressedRequest(t *testing.T, want bool, request *http.Request) {
	wantEnc := ""
	if want {
		wantEnc = "gzip"
	}
	gotEnc := request.Header.Get("Content-Encoding")
	if gotEnc != wantEnc {
		t.Errorf("got Content-Encoding=%q, want %q", gotEnc, wantEnc)
	}
	body, err := io.ReadAll(request.Body)
	if err != nil {
		t.Fatal(err)
	}
	compressed := bytes.HasPrefix(body, []byte{0x1f, 0x8b})
	if compressed != want {
		t.Errorf("got compressed=%t, want %t", compressed, want)
	}
}

func TestURLPath(t *testing.T) {
	tests := []struct {
		in  Id
		out string
	}{
		{
			Id{
				Namespace:    "ns-with-/",
				Type:         "type-with-/",
				UserSpecific: "user",
			},
			"/document/v1/ns-with-%2F/type-with-%2F/docid/user",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				Number:       ptr(int64(123)),
				UserSpecific: "user",
			},
			"/document/v1/ns/type/number/123/user",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				Group:        "foo",
				UserSpecific: "user",
			},
			"/document/v1/ns/type/group/foo/user",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "user::specific",
			},
			"/document/v1/ns/type/docid/user::specific",
		},
		{
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: ":",
			},
			"/document/v1/ns/type/docid/:",
		},
	}
	for i, tt := range tests {
		path := urlPath(tt.in)
		if path != tt.out {
			t.Errorf("#%d: documentPath(%q) = %s, want %s", i, tt.in, path, tt.out)
		}
	}
}

func TestClientFeedURL(t *testing.T) {
	tests := []struct {
		in     Document
		method string
		url    string
	}{
		{
			Document{Id: mustParseId("id:ns:type::user")},
			"POST",
			"https://example.com/document/v1/ns/type/docid/user?foo=ba%2Fr",
		},
		{
			Document{
				Id:        mustParseId("id:ns:type::user"),
				Operation: OperationUpdate,
				Create:    true,
				Condition: "false",
			},
			"PUT",
			"https://example.com/document/v1/ns/type/docid/user?condition=false&create=true&foo=ba%2Fr",
		},
		{
			Document{
				Id:        mustParseId("id:ns:type::user"),
				Operation: OperationRemove,
			},
			"DELETE",
			"https://example.com/document/v1/ns/type/docid/user?foo=ba%2Fr",
		},
	}
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com",
	}, []util.HTTPClient{&httpClient})
	for i, tt := range tests {
		moreParams := url.Values{}
		moreParams.Set("foo", "ba/r")
		method, u, err := client.feedURL(tt.in, moreParams)
		if err != nil {
			t.Errorf("#%d: got unexpected error = %s, want none", i, err)
		}
		if u.String() != tt.url || method != tt.method {
			t.Errorf("#%d: URL() = (%s, %s), want (%s, %s)", i, method, u.String(), tt.method, tt.url)
		}
	}
}

func benchmarkClientSend(b *testing.B, compression Compression, document Document) {
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		Compression: compression,
		BaseURL:     "https://example.com:1337",
		Timeout:     time.Duration(5 * time.Second),
	}, []util.HTTPClient{&httpClient})
	b.ResetTimer() // ignore setup
	for n := 0; n < b.N; n++ {
		client.Send(document)
	}
}

func BenchmarkClientSend(b *testing.B) {
	doc := Document{Create: true, Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "my document"}}`)}
	benchmarkClientSend(b, CompressionNone, doc)
}

func BenchmarkClientSendCompressed(b *testing.B) {
	doc := Document{Create: true, Id: mustParseId("id:ns:type::doc1"), Operation: OperationUpdate, Body: []byte(`{"fields":{"foo": "my document"}}`)}
	benchmarkClientSend(b, CompressionGzip, doc)
}
