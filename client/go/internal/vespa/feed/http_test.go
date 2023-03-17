package feed

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"reflect"
	"testing"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

type manualClock struct {
	t    time.Time
	tick time.Duration
}

func (c *manualClock) now() time.Time {
	t := c.t
	c.t = c.t.Add(c.tick)
	return t
}

func TestClientSend(t *testing.T) {
	docs := []Document{
		mustParseDocument(Document{Create: true, UpdateId: "id:ns:type::doc1", Fields: json.RawMessage(`{"foo": "123"}`)}),
		mustParseDocument(Document{Create: true, UpdateId: "id:ns:type::doc2", Fields: json.RawMessage(`{"foo": "456"}`)}),
		mustParseDocument(Document{Create: true, UpdateId: "id:ns:type::doc3", Fields: json.RawMessage(`{"baz": "789"}`)}),
	}
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, &httpClient)
	clock := manualClock{t: time.Now(), tick: time.Second}
	client.now = clock.now
	for i, doc := range docs {
		if i < 2 {
			httpClient.NextResponseString(200, `{"message":"All good!"}`)
		} else {
			httpClient.NextResponseString(502, `{"message":"Good bye, cruel world!"}`)
		}
		res := client.Send(doc)
		if res.Err != nil {
			t.Fatalf("got unexpected error %q", res.Err)
		}
		r := httpClient.LastRequest
		if r.Method != http.MethodPut {
			t.Errorf("got r.Method = %q, want %q", r.Method, http.MethodPut)
		}
		wantURL := fmt.Sprintf("https://example.com:1337/document/v1/ns/type/docid/%s?create=true&timeout=5000ms", doc.Id.UserSpecific)
		if r.URL.String() != wantURL {
			t.Errorf("got r.URL = %q, want %q", r.URL, wantURL)
		}
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Fatalf("got unexpected error %q", err)
		}
		wantBody := doc.Body()
		if !bytes.Equal(body, wantBody) {
			t.Errorf("got r.Body = %q, want %q", string(body), string(wantBody))
		}
	}
	stats := client.Stats()
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
