// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

type TokenResponse struct {
	AccessToken string `json:"access_token"`
	IDToken     string `json:"id_token"`
	TokenType   string `json:"token_type"`
	ExpiresIn   int    `json:"expires_in"`
}

type TokenRetriever struct {
	Authenticator *Authenticator
	Secrets       SecretStore
	Client        *http.Client
}

// Delete deletes the given system from the secrets' storage.
func (t *TokenRetriever) Delete(system string) error {
	return t.Secrets.Delete(SecretsNamespace, system)
}

// Refresh gets a new access token from the provided refresh token,
// The request is used the default client_id and endpoint for device authentication.
func (t *TokenRetriever) Refresh(ctx context.Context, system string) (TokenResponse, error) {
	// get stored refresh token:
	refreshToken, err := t.Secrets.Get(SecretsNamespace, system)
	if err != nil {
		return TokenResponse{}, fmt.Errorf("cannot get the stored refresh token: %w", err)
	}
	if refreshToken == "" {
		return TokenResponse{}, errors.New("cannot use the stored refresh token: the token is empty")
	}
	// get access token:
	r, err := t.Client.PostForm(t.Authenticator.OauthTokenEndpoint, url.Values{
		"grant_type":    {"refresh_token"},
		"client_id":     {t.Authenticator.ClientID},
		"refresh_token": {refreshToken},
	})
	if err != nil {
		return TokenResponse{}, fmt.Errorf("cannot get a new access token from the refresh token: %w", err)
	}

	defer r.Body.Close()
	if r.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(r.Body)
		res := struct {
			Description string `json:"error_description"`
		}{}
		if json.Unmarshal(b, &res) == nil {
			return TokenResponse{}, errors.New(strings.ToLower(strings.TrimSuffix(res.Description, ".")))
		}
		return TokenResponse{}, fmt.Errorf("cannot get a new access token from the refresh token: %s", string(b))
	}

	var res TokenResponse
	err = json.NewDecoder(r.Body).Decode(&res)
	if err != nil {
		return TokenResponse{}, fmt.Errorf("cannot decode response: %w", err)
	}

	return res, nil
}
