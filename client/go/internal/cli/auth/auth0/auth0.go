// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth0

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/cli/auth"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

const (
	accessTokenExpiry = 5 * time.Minute
	reauthMessage     = "re-authenticate with 'vespa auth login'"
)

// Credentials holds the credentials retrieved from Auth0.
type Credentials struct {
	AccessToken string    `json:"access_token,omitempty"`
	Scopes      []string  `json:"scopes,omitempty"`
	ExpiresAt   time.Time `json:"expires_at"`
}

// Client is a client for the Auth0 service.
type Client struct {
	httpClient    util.HTTPClient
	Authenticator *auth.Authenticator // TODO: Make this private
	options       Options
	provider      auth0Provider
}

type Options struct {
	ConfigPath string
	SystemName string
	SystemURL  string
}

// config is the root type of the persisted config
type config struct {
	Version   int       `json:"version"`
	Providers providers `json:"providers"`
}

type providers struct {
	Auth0 auth0Provider `json:"auth0"`
}

type auth0Provider struct {
	Version int                    `json:"version"`
	Systems map[string]Credentials `json:"systems"`
}

// flowConfig represents the authorization flow configuration retrieved from a Vespa system.
type flowConfig struct {
	Audience           string `json:"audience"`
	ClientID           string `json:"client-id"`
	DeviceCodeEndpoint string `json:"device-code-endpoint"`
	OauthTokenEndpoint string `json:"oauth-token-endpoint"`
}

func cancelOnInterrupt() context.Context {
	ctx, cancel := context.WithCancel(context.Background())
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt)
	go func() {
		<-ch
		defer cancel()
		os.Exit(0)
	}()
	return ctx
}

// NewClient constructs a new Auth0 client, storing configuration in the given configPath. The client will be configured for
// use in the given Vespa system.
func NewClient(httpClient util.HTTPClient, options Options) (*Client, error) {
	a := Client{}
	a.httpClient = httpClient
	a.options = options
	c, err := a.getDeviceFlowConfig()
	if err != nil {
		return nil, err
	}
	a.Authenticator = &auth.Authenticator{
		Audience:           c.Audience,
		ClientID:           c.ClientID,
		DeviceCodeEndpoint: c.DeviceCodeEndpoint,
		OauthTokenEndpoint: c.OauthTokenEndpoint,
	}
	provider, err := readConfig(options.ConfigPath)
	if err != nil {
		return nil, err
	}
	a.provider = provider
	return &a, nil
}

func (a *Client) getDeviceFlowConfig() (flowConfig, error) {
	url := a.options.SystemURL + "/auth0/v1/device-flow-config"
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return flowConfig{}, err
	}
	r, err := a.httpClient.Do(req, time.Second*30)
	if err != nil {
		return flowConfig{}, fmt.Errorf("auth0: failed to get device flow config: %w", err)
	}
	defer r.Body.Close()
	if r.StatusCode/100 != 2 {
		return flowConfig{}, fmt.Errorf("auth0: failed to get device flow config: got response code %d from %s", r.StatusCode, url)
	}
	var cfg flowConfig
	if err := json.NewDecoder(r.Body).Decode(&cfg); err != nil {
		return flowConfig{}, fmt.Errorf("auth0: failed to decode response: %w", err)
	}
	return cfg, nil
}

func (a *Client) Authenticate(request *http.Request) error {
	accessToken, err := a.AccessToken()
	if err != nil {
		return err
	}
	if request.Header == nil {
		request.Header = make(http.Header)
	}
	request.Header.Set("Authorization", "Bearer "+accessToken)
	return nil
}

// AccessToken returns an access token for the configured system, refreshing it if necessary.
func (a *Client) AccessToken() (string, error) {
	creds, ok := a.provider.Systems[a.options.SystemName]
	if !ok {
		return "", fmt.Errorf("auth0: system %s is not configured: %s", a.options.SystemName, reauthMessage)
	} else if creds.AccessToken == "" {
		return "", fmt.Errorf("auth0: access token missing: %s", reauthMessage)
	} else if scopesChanged(creds) {
		return "", fmt.Errorf("auth0: authentication scopes changed: %s", reauthMessage)
	} else if isExpired(creds.ExpiresAt, accessTokenExpiry) {
		// check if the stored access token is expired:
		// use the refresh token to get a new access token:
		tr := &auth.TokenRetriever{
			Authenticator: a.Authenticator,
			Secrets:       &auth.Keyring{},
			Client:        http.DefaultClient,
		}
		resp, err := tr.Refresh(cancelOnInterrupt(), a.options.SystemName)
		if err != nil {
			return "", fmt.Errorf("auth0: failed to renew access token: %w: %s", err, reauthMessage)
		} else {
			// persist the updated system with renewed access token
			creds.AccessToken = resp.AccessToken
			creds.ExpiresAt = time.Now().Add(time.Duration(resp.ExpiresIn) * time.Second)
			if err := a.WriteCredentials(creds); err != nil {
				return "", err
			}
		}
	}
	return creds.AccessToken, nil
}

func isExpired(t time.Time, ttl time.Duration) bool { return time.Now().Add(ttl).After(t) }

func scopesChanged(s Credentials) bool {
	required := auth.RequiredScopes()
	current := s.Scopes
	if len(required) != len(current) {
		return true
	}
	sort.Strings(required)
	sort.Strings(current)
	for i := range s.Scopes {
		if required[i] != current[i] {
			return true
		}
	}
	return false
}

// WriteCredentials writes given credentials to the configuration file.
func (a *Client) WriteCredentials(credentials Credentials) error {
	if a.provider.Systems == nil {
		a.provider.Systems = make(map[string]Credentials)
	}
	a.provider.Systems[a.options.SystemName] = credentials
	if err := writeConfig(a.provider, a.options.ConfigPath); err != nil {
		return fmt.Errorf("auth0: failed to write config: %w", err)
	}
	return nil
}

// RemoveCredentials removes credentials for the system configured in this client.
func (a *Client) RemoveCredentials() error {
	tr := &auth.TokenRetriever{Secrets: &auth.Keyring{}}
	if err := tr.Delete(a.options.SystemName); err != nil {
		return fmt.Errorf("auth0: failed to remove system %s from secret storage: %w", a.options.SystemName, err)
	}
	delete(a.provider.Systems, a.options.SystemName)
	if err := writeConfig(a.provider, a.options.ConfigPath); err != nil {
		return fmt.Errorf("auth0: failed to write config: %w", err)
	}
	return nil
}

func writeConfig(provider auth0Provider, path string) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}
	version := 1
	provider.Version = version
	r := config{
		Version: version,
		Providers: providers{
			Auth0: provider,
		},
	}
	jsonConfig, err := json.MarshalIndent(r, "", "    ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, jsonConfig, 0600)
}

func readConfig(path string) (auth0Provider, error) {
	f, err := os.Open(path)
	if err != nil {
		if os.IsNotExist(err) {
			return auth0Provider{}, nil
		}
		return auth0Provider{}, err
	}
	defer f.Close()
	cfg := config{}
	if err := json.NewDecoder(f).Decode(&cfg); err != nil {
		return auth0Provider{}, err
	}
	auth0Provider := cfg.Providers.Auth0
	if auth0Provider.Systems == nil {
		auth0Provider.Systems = make(map[string]Credentials)
	}
	return auth0Provider, nil
}
