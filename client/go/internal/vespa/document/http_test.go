package document

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
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

func TestURLPath(t *testing.T) {
	tests := []struct {
		in  DocumentId
		out string
	}{
		{
			DocumentId{
				Namespace:    "ns-with-/",
				Type:         "type-with-/",
				UserSpecific: "user",
			},
			"/document/v1/ns-with-%2F/type-with-%2F/docid/user",
		},
		{
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				Number:       ptr(int64(123)),
				UserSpecific: "user",
			},
			"/document/v1/ns/type/number/123/user",
		},
		{
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				Group:        "foo",
				UserSpecific: "user",
			},
			"/document/v1/ns/type/group/foo/user",
		},
		{
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "user::specific",
			},
			"/document/v1/ns/type/docid/user::specific",
		},
		{
			DocumentId{
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
			mustParseDocument(Document{
				IdString: "id:ns:type::user",
			}),
			"POST",
			"https://example.com/document/v1/ns/type/docid/user?foo=ba%2Fr",
		},
		{
			mustParseDocument(Document{
				UpdateId:  "id:ns:type::user",
				Create:    true,
				Condition: "false",
			}),
			"PUT",
			"https://example.com/document/v1/ns/type/docid/user?condition=false&create=true&foo=ba%2Fr",
		},
		{
			mustParseDocument(Document{
				RemoveId: "id:ns:type::user",
			}),
			"DELETE",
			"https://example.com/document/v1/ns/type/docid/user?foo=ba%2Fr",
		},
	}
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com",
	}, &httpClient)
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
