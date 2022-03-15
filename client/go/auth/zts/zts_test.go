package zts

import (
	"crypto/tls"
	"testing"

	"github.com/vespa-engine/vespa/client/go/mock"
)

func TestAccessToken(t *testing.T) {
	httpClient := mock.HTTPClient{}
	client, err := NewClient("http://example.com", &httpClient)
	if err != nil {
		t.Fatal(err)
	}
	httpClient.NextResponseString(400, `{"message": "bad request"}`)
	_, err = client.AccessToken("vespa.vespa", tls.Certificate{})
	if err == nil {
		t.Fatal("want error for non-ok response status")
	}
	httpClient.NextResponseString(200, `{"access_token": "foo bar"}`)
	token, err := client.AccessToken("vespa.vespa", tls.Certificate{})
	if err != nil {
		t.Fatal(err)
	}
	want := "foo bar"
	if token != want {
		t.Errorf("got %q, want %q", token, want)
	}
}
