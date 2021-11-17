// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const (
	waitThresholdInSeconds = 3
	// SecretsNamespace namespace used to set/get values from the keychain
	SecretsNamespace = "vespa-cli"
)

var requiredScopes = []string{"openid", "offline_access"}

type Authenticator struct {
	Audience           string
	ClientID           string
	DeviceCodeEndpoint string
	OauthTokenEndpoint string
}

// SecretStore provides access to stored sensitive data.
type SecretStore interface {
	// Get gets the secret
	Get(namespace, key string) (string, error)
	// Delete removes the secret
	Delete(namespace, key string) error
}

type Result struct {
	RefreshToken string
	AccessToken  string
	ExpiresIn    int64
}

type State struct {
	DeviceCode      string `json:"device_code"`
	UserCode        string `json:"user_code"`
	VerificationURI string `json:"verification_uri_complete"`
	ExpiresIn       int    `json:"expires_in"`
	Interval        int    `json:"interval"`
}

// RequiredScopes returns the scopes used for login.
func RequiredScopes() []string { return requiredScopes }

func (s *State) IntervalDuration() time.Duration {
	return time.Duration(s.Interval+waitThresholdInSeconds) * time.Second
}

// Start kicks-off the device authentication flow
// by requesting a device code from Auth0,
// The returned state contains the URI for the next step of the flow.
func (a *Authenticator) Start(ctx context.Context) (State, error) {
	s, err := a.getDeviceCode(ctx)
	if err != nil {
		return State{}, fmt.Errorf("cannot get device code: %w", err)
	}
	return s, nil
}

// Wait waits until the user is logged in on the browser.
func (a *Authenticator) Wait(ctx context.Context, state State) (Result, error) {
	t := time.NewTicker(state.IntervalDuration())
	for {
		select {
		case <-ctx.Done():
			return Result{}, ctx.Err()
		case <-t.C:
			data := url.Values{
				"client_id":   {a.ClientID},
				"grant_type":  {"urn:ietf:params:oauth:grant-type:device_code"},
				"device_code": {state.DeviceCode},
			}
			r, err := http.PostForm(a.OauthTokenEndpoint, data)
			if err != nil {
				return Result{}, fmt.Errorf("cannot get device code: %w", err)
			}
			defer r.Body.Close()

			var res struct {
				AccessToken      string  `json:"access_token"`
				IDToken          string  `json:"id_token"`
				RefreshToken     string  `json:"refresh_token"`
				Scope            string  `json:"scope"`
				ExpiresIn        int64   `json:"expires_in"`
				TokenType        string  `json:"token_type"`
				Error            *string `json:"error,omitempty"`
				ErrorDescription string  `json:"error_description,omitempty"`
			}

			err = json.NewDecoder(r.Body).Decode(&res)
			if err != nil {
				return Result{}, fmt.Errorf("cannot decode response: %w", err)
			}

			if res.Error != nil {
				if *res.Error == "authorization_pending" {
					continue
				}
				return Result{}, errors.New(res.ErrorDescription)
			}

			return Result{
				RefreshToken: res.RefreshToken,
				AccessToken:  res.AccessToken,
				ExpiresIn:    res.ExpiresIn,
			}, nil
		}
	}
}

func (a *Authenticator) getDeviceCode(ctx context.Context) (State, error) {
	data := url.Values{
		"client_id": {a.ClientID},
		"scope":     {strings.Join(requiredScopes, " ")},
		"audience":  {a.Audience},
	}
	r, err := http.PostForm(a.DeviceCodeEndpoint, data)
	if err != nil {
		return State{}, fmt.Errorf("cannot get device code: %w", err)
	}
	defer r.Body.Close()
	var res State
	err = json.NewDecoder(r.Body).Decode(&res)
	if err != nil {
		return State{}, fmt.Errorf("cannot decode response: %w", err)
	}
	return res, nil
}
