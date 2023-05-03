package zts

import (
	"testing"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

type manualClock struct{ t time.Time }

func (c *manualClock) now() time.Time          { return c.t }
func (c *manualClock) advance(d time.Duration) { c.t = c.t.Add(d) }

func TestAccessToken(t *testing.T) {
	httpClient := mock.HTTPClient{}
	client, err := NewClient(&httpClient, "vespa.vespa", "http://example.com")
	if err != nil {
		t.Fatal(err)
	}
	clock := &manualClock{t: time.Now()}
	client.now = clock.now
	httpClient.NextResponseString(400, `{"message": "bad request"}`)
	_, err = client.AccessToken()
	if err == nil {
		t.Fatal("want error for non-ok response status")
	}
	httpClient.NextResponseString(200, `{"access_token": "foo", "expires_in": 3600}`)
	token, err := client.AccessToken()
	if err != nil {
		t.Fatal(err)
	}

	// Token is cached
	expiresAt := clock.now().Add(time.Hour)
	assertToken(t, Token{Value: "foo", ExpiresAt: expiresAt}, token)
	clock.advance(54 * time.Minute)
	assertToken(t, Token{Value: "foo", ExpiresAt: expiresAt}, token)

	// Token is renewed when nearing expiry
	clock.advance(time.Minute + time.Second)
	httpClient.NextResponseString(200, `{"access_token": "bar", "expires_in": 1800}`)
	token, err = client.AccessToken()
	if err != nil {
		t.Fatal(err)
	}
	expiresAt = clock.now().Add(30 * time.Minute)
	assertToken(t, Token{Value: "bar", ExpiresAt: expiresAt}, token)
}

func assertToken(t *testing.T, want, got Token) {
	if want.Value != got.Value {
		t.Errorf("got Value=%q, want %q", got.Value, want.Value)
	}
	if want.ExpiresAt != got.ExpiresAt {
		t.Errorf("got ExpiresAt=%s, want %s", got.ExpiresAt, want.ExpiresAt)
	}
}
