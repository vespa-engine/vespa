// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const (
	// PKCECallbackPortFirst and PKCECallbackPortCount define the range of loopback ports probed for the
	// callback server in the PKCE flow. The range lies within 24007-24241, which is unassigned by IANA. Every
	// redirect URI http://127.0.0.1:<port>/callback in this range must be an allowed callback URL for the client
	// in the authorization server.
	PKCECallbackPortFirst = 24180
	PKCECallbackPortCount = 5
	pkceCallbackPath      = "/callback"
	// PKCETimeout is the maximum time to wait for the user to complete login in the browser.
	PKCETimeout = 5 * time.Minute
)

// callbackPorts returns the ports to probe for the callback server. Overridden in tests.
var callbackPorts = func() []int {
	ports := make([]int, PKCECallbackPortCount)
	for i := range ports {
		ports[i] = PKCECallbackPortFirst + i
	}
	return ports
}

// ErrPKCETimeout is returned when the user does not complete the PKCE flow in time.
var ErrPKCETimeout = errors.New("timed out waiting for login to complete in browser")

// PKCEState holds the state of an ongoing authorization code flow with PKCE.
type PKCEState struct {
	// AuthorizeURL is the URL the user must open in a browser to log in.
	AuthorizeURL string
	verifier     string
	state        string
	redirectURI  string
	listener     net.Listener
}

// Close releases the resources held by this state. It is safe to call multiple times.
func (s *PKCEState) Close() error {
	if s.listener == nil {
		return nil
	}
	err := s.listener.Close()
	s.listener = nil
	return err
}

type callbackResult struct {
	code string
	err  error
}

// StartPKCE starts the authorization code flow with PKCE by starting a local callback server and
// building the authorization URL. The caller must call Close on the returned state when done.
func (a *Authenticator) StartPKCE(ctx context.Context) (*PKCEState, error) {
	authorizeEndpoint, err := a.authorizationEndpoint()
	if err != nil {
		return nil, err
	}
	listener, err := listenCallback(ctx)
	if err != nil {
		return nil, err
	}
	verifier, err := randomString(32)
	if err != nil {
		listener.Close()
		return nil, err
	}
	state, err := randomString(16)
	if err != nil {
		listener.Close()
		return nil, err
	}
	redirectURI := fmt.Sprintf("http://127.0.0.1:%d%s", listener.Addr().(*net.TCPAddr).Port, pkceCallbackPath)
	params := url.Values{
		"response_type":         {"code"},
		"client_id":             {a.ClientID},
		"redirect_uri":          {redirectURI},
		"scope":                 {strings.Join(requiredScopes, " ")},
		"audience":              {a.Audience},
		"state":                 {state},
		"code_challenge":        {codeChallenge(verifier)},
		"code_challenge_method": {"S256"},
	}
	return &PKCEState{
		AuthorizeURL: authorizeEndpoint + "?" + params.Encode(),
		verifier:     verifier,
		state:        state,
		redirectURI:  redirectURI,
		listener:     listener,
	}, nil
}

// listenCallback binds the callback server to the first available port in callbackPorts on 127.0.0.1.
func listenCallback(ctx context.Context) (net.Listener, error) {
	var lc net.ListenConfig
	ports := callbackPorts()
	var lastErr error
	for _, port := range ports {
		listener, err := lc.Listen(ctx, "tcp4", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)))
		if err == nil {
			return listener, nil
		}
		lastErr = err
	}
	if len(ports) == 0 {
		return nil, errors.New("cannot start local callback server: no ports to try")
	}
	return nil, fmt.Errorf("cannot start local callback server: no available port in %d-%d: %w", ports[0], ports[len(ports)-1], lastErr)
}

// WaitPKCE waits for the browser to redirect back to the local callback server, and exchanges the received
// authorization code for tokens. It gives up after PKCETimeout.
func (a *Authenticator) WaitPKCE(ctx context.Context, s *PKCEState) (Result, error) {
	defer s.Close()
	if s.listener == nil {
		return Result{}, errors.New("pkce flow is not started")
	}
	ctx, cancel := context.WithTimeout(ctx, PKCETimeout)
	defer cancel()

	results := make(chan callbackResult, 1)
	mux := http.NewServeMux()
	mux.HandleFunc(pkceCallbackPath, func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		var res callbackResult
		switch {
		case q.Get("state") != s.state:
			res.err = errors.New("invalid state in authorization response")
		case q.Get("error") != "":
			desc := q.Get("error_description")
			if desc == "" {
				desc = q.Get("error")
			}
			res.err = errors.New(desc)
		case q.Get("code") == "":
			res.err = errors.New("no authorization code in authorization response")
		default:
			res.code = q.Get("code")
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		if res.err != nil {
			w.WriteHeader(http.StatusBadRequest)
			fmt.Fprint(w, callbackPage("Login failed", "Return to the terminal for details."))
		} else {
			fmt.Fprint(w, callbackPage("Login successful", "You can close this window and return to the terminal."))
		}
		select {
		case results <- res:
		default:
		}
	})
	server := &http.Server{Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	go server.Serve(s.listener)
	defer server.Close()

	var res callbackResult
	select {
	case <-ctx.Done():
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			return Result{}, ErrPKCETimeout
		}
		return Result{}, ctx.Err()
	case res = <-results:
	}
	if res.err != nil {
		return Result{}, res.err
	}
	return a.exchangeCode(ctx, res.code, s)
}

func (a *Authenticator) exchangeCode(ctx context.Context, code string, s *PKCEState) (Result, error) {
	data := url.Values{
		"grant_type":    {"authorization_code"},
		"client_id":     {a.ClientID},
		"code":          {code},
		"code_verifier": {s.verifier},
		"redirect_uri":  {s.redirectURI},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, a.OauthTokenEndpoint, strings.NewReader(data.Encode()))
	if err != nil {
		return Result{}, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r, err := http.DefaultClient.Do(req)
	if err != nil {
		return Result{}, fmt.Errorf("cannot exchange authorization code: %w", err)
	}
	defer r.Body.Close()
	body, err := io.ReadAll(r.Body)
	if err != nil {
		return Result{}, fmt.Errorf("cannot read token response: %w", err)
	}
	var res struct {
		AccessToken      string `json:"access_token"`
		RefreshToken     string `json:"refresh_token"`
		ExpiresIn        int64  `json:"expires_in"`
		Error            string `json:"error"`
		ErrorDescription string `json:"error_description"`
	}
	if err := json.Unmarshal(body, &res); err != nil {
		return Result{}, fmt.Errorf("cannot decode token response: %w", err)
	}
	if r.StatusCode != http.StatusOK || res.Error != "" {
		if res.ErrorDescription != "" {
			return Result{}, errors.New(res.ErrorDescription)
		}
		return Result{}, fmt.Errorf("cannot exchange authorization code: got status %d: %s", r.StatusCode, string(body))
	}
	return Result{RefreshToken: res.RefreshToken, AccessToken: res.AccessToken, ExpiresIn: res.ExpiresIn}, nil
}

// authorizationEndpoint returns the configured authorization endpoint, or derives it from the token endpoint.
func (a *Authenticator) authorizationEndpoint() (string, error) {
	if a.AuthorizationEndpoint != "" {
		return a.AuthorizationEndpoint, nil
	}
	u, err := url.Parse(a.OauthTokenEndpoint)
	if err != nil || u.Scheme == "" || u.Host == "" {
		return "", fmt.Errorf("cannot derive authorization endpoint from token endpoint %q", a.OauthTokenEndpoint)
	}
	return u.Scheme + "://" + u.Host + "/authorize", nil
}

func randomString(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", fmt.Errorf("cannot generate random data: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func codeChallenge(verifier string) string {
	sum := sha256.Sum256([]byte(verifier))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

func callbackPage(title, message string) string {
	return `<!doctype html><html><head><meta charset="utf-8"><title>Vespa CLI - ` + title + `</title></head>` +
		`<body style="font-family:sans-serif;text-align:center;margin-top:4em">` +
		`<h1>` + title + `</h1><p>` + message + `</p></body></html>`
}
