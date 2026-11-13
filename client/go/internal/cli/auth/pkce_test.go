// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"context"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestAuthorizationEndpoint(t *testing.T) {
	a := Authenticator{OauthTokenEndpoint: "https://example.invalid/oauth/token"}
	ep, err := a.authorizationEndpoint()
	require.Nil(t, err)
	assert.Equal(t, "https://example.invalid/authorize", ep)

	a.AuthorizationEndpoint = "https://other.invalid/auth"
	ep, err = a.authorizationEndpoint()
	require.Nil(t, err)
	assert.Equal(t, "https://other.invalid/auth", ep)
}

func withRandomCallbackPort(t *testing.T) {
	old := callbackPorts
	callbackPorts = func() []int { return []int{0} }
	t.Cleanup(func() { callbackPorts = old })
}

func TestListenCallbackProbesPorts(t *testing.T) {
	busy, err := net.Listen("tcp4", "127.0.0.1:0")
	require.Nil(t, err)
	defer busy.Close()
	busyPort := busy.Addr().(*net.TCPAddr).Port
	free, err := net.Listen("tcp4", "127.0.0.1:0")
	require.Nil(t, err)
	freePort := free.Addr().(*net.TCPAddr).Port
	free.Close()

	old := callbackPorts
	t.Cleanup(func() { callbackPorts = old })
	callbackPorts = func() []int { return []int{busyPort, freePort} }
	l, err := listenCallback(context.Background())
	require.Nil(t, err)
	defer l.Close()
	addr := l.Addr().(*net.TCPAddr)
	assert.Equal(t, freePort, addr.Port)
	assert.Equal(t, "127.0.0.1", addr.IP.String())

	callbackPorts = func() []int { return []int{busyPort} }
	_, err = listenCallback(context.Background())
	assert.NotNil(t, err)
}

func TestDefaultCallbackPorts(t *testing.T) {
	ports := callbackPorts()
	assert.Equal(t, 5, len(ports))
	assert.Equal(t, 24180, ports[0])
	assert.Equal(t, 24184, ports[4])
}

func startPKCE(t *testing.T, tokenEndpoint string) (*Authenticator, *PKCEState, url.Values) {
	withRandomCallbackPort(t)
	a := &Authenticator{ClientID: "my-client", Audience: "my-audience", OauthTokenEndpoint: tokenEndpoint}
	state, err := a.StartPKCE(context.Background())
	require.Nil(t, err)
	t.Cleanup(func() { state.Close() })
	u, err := url.Parse(state.AuthorizeURL)
	require.Nil(t, err)
	q := u.Query()
	assert.Equal(t, "code", q.Get("response_type"))
	assert.Equal(t, "my-client", q.Get("client_id"))
	assert.Equal(t, "my-audience", q.Get("audience"))
	assert.Equal(t, "openid offline_access", q.Get("scope"))
	assert.Equal(t, "S256", q.Get("code_challenge_method"))
	assert.Equal(t, codeChallenge(state.verifier), q.Get("code_challenge"))
	assert.Equal(t, state.redirectURI, q.Get("redirect_uri"))
	return a, state, q
}

func callback(t *testing.T, redirectURI string, params url.Values) int {
	r, err := http.Get(redirectURI + "?" + params.Encode())
	require.Nil(t, err)
	defer r.Body.Close()
	io.Copy(io.Discard, r.Body)
	return r.StatusCode
}

func TestPKCEFlow(t *testing.T) {
	var form url.Values
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.Nil(t, r.ParseForm())
		form = r.PostForm
		w.Write([]byte(`{"access_token":"at","refresh_token":"rt","expires_in":3600}`))
	}))
	defer tokenServer.Close()
	a, state, q := startPKCE(t, tokenServer.URL+"/oauth/token")

	type result struct {
		res Result
		err error
	}
	done := make(chan result, 1)
	go func() {
		res, err := a.WaitPKCE(context.Background(), state)
		done <- result{res, err}
	}()
	assert.Equal(t, 200, callback(t, q.Get("redirect_uri"), url.Values{"code": {"the-code"}, "state": {q.Get("state")}}))
	r := <-done
	require.Nil(t, r.err)
	assert.Equal(t, Result{AccessToken: "at", RefreshToken: "rt", ExpiresIn: 3600}, r.res)
	assert.Equal(t, "authorization_code", form.Get("grant_type"))
	assert.Equal(t, "the-code", form.Get("code"))
	assert.Equal(t, "my-client", form.Get("client_id"))
	assert.Equal(t, state.verifier, form.Get("code_verifier"))
	assert.Equal(t, q.Get("redirect_uri"), form.Get("redirect_uri"))
}

func TestPKCEFlowErrors(t *testing.T) {
	a, state, q := startPKCE(t, "https://example.invalid/oauth/token")
	done := make(chan error, 1)
	go func() {
		_, err := a.WaitPKCE(context.Background(), state)
		done <- err
	}()
	assert.Equal(t, 400, callback(t, q.Get("redirect_uri"), url.Values{"code": {"c"}, "state": {"wrong"}}))
	assert.EqualError(t, <-done, "invalid state in authorization response")

	a, state, q = startPKCE(t, "https://example.invalid/oauth/token")
	go func() {
		_, err := a.WaitPKCE(context.Background(), state)
		done <- err
	}()
	assert.Equal(t, 400, callback(t, q.Get("redirect_uri"), url.Values{"error": {"access_denied"}, "error_description": {"600"}, "state": {q.Get("state")}}))
	assert.EqualError(t, <-done, "600")
}
