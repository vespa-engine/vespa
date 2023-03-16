package feed

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"testing"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestClientSend(t *testing.T) {
	doc := Document{Create: true, UpdateId: "id:ns:type::doc1", Fields: json.RawMessage(`{"foo": "123"}`)}
	httpClient := mock.HTTPClient{}
	client := NewClient(ClientOptions{
		BaseURL: "https://example.com:1337",
		Timeout: time.Duration(5 * time.Second),
	}, &httpClient)
	_, err := client.Send(doc)
	if err != nil {
		t.Fatalf("got unexpected error %q", err)
	}
	r := httpClient.LastRequest
	if r.Method != http.MethodPut {
		t.Errorf("got r.Method = %q, want %q", r.Method, http.MethodPut)
	}
	wantURL := "https://example.com:1337/document/v1/ns/type/docid/doc1?create=true&timeout=5000ms"
	if r.URL.String() != wantURL {
		t.Errorf("got r.URL = %q, want %q", r.URL, wantURL)
	}
	body, err := io.ReadAll(r.Body)
	if err != nil {
		t.Fatalf("got unexpected error %q", err)
	}
	wantBody := []byte(`{"fields":{"foo": "123"}}`)
	if !bytes.Equal(body, wantBody) {
		t.Errorf("got r.Body = %q, want %q", string(body), string(wantBody))
	}
}
