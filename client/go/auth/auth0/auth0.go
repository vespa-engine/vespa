// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth0

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"github.com/lestrrat-go/jwx/jwt"
	"github.com/vespa-engine/vespa/client/go/auth"
)

const accessTokenExpThreshold = 5 * time.Minute

var errUnauthenticated = errors.New("not logged in. Try 'vespa auth login'")

type configJsonFormat struct {
	Version   int       `json:"version"`
	Providers providers `json:"providers"`
}

type providers struct {
	Config config `json:"auth0"`
}

type config struct {
	Version int                `json:"version"`
	Systems map[string]*System `json:"systems"`
}

type System struct {
	Name        string    `json:"-"`
	AccessToken string    `json:"access_token,omitempty"`
	Scopes      []string  `json:"scopes,omitempty"`
	ExpiresAt   time.Time `json:"expires_at"`
}

type Auth0 struct {
	Authenticator *auth.Authenticator
	system        string
	systemApiUrl  string
	initOnce      sync.Once
	errOnce       error
	Path          string
	config        config
}

type authCfg struct {
	Audience           string `json:"audience"`
	ClientID           string `json:"client-id"`
	DeviceCodeEndpoint string `json:"device-code-endpoint"`
	OauthTokenEndpoint string `json:"oauth-token-endpoint"`
}

func ContextWithCancel() context.Context {
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

// GetAuth0 will try to initialize the config context, as well as figure out if
// there's a readily available system.
func GetAuth0(configPath string, systemName string, systemApiUrl string) (*Auth0, error) {
	a := Auth0{}
	a.Path = configPath
	a.system = systemName
	a.systemApiUrl = systemApiUrl
	c, err := a.getDeviceFlowConfig()
	if err != nil {
		return nil, fmt.Errorf("cannot get auth config: %w", err)
	}
	a.Authenticator = &auth.Authenticator{
		Audience:           c.Audience,
		ClientID:           c.ClientID,
		DeviceCodeEndpoint: c.DeviceCodeEndpoint,
		OauthTokenEndpoint: c.OauthTokenEndpoint,
	}
	return &a, nil
}

func (a *Auth0) getDeviceFlowConfig() (authCfg, error) {
	systemApiUrl, _ := url.Parse(a.systemApiUrl + "/auth0/v1/device-flow-config")
	r, err := http.Get(systemApiUrl.String())
	if err != nil {
		return authCfg{}, fmt.Errorf("cannot get auth config: %w", err)
	}
	defer r.Body.Close()
	var res authCfg
	err = json.NewDecoder(r.Body).Decode(&res)
	if err != nil {
		return authCfg{}, fmt.Errorf("cannot decode response: %w", err)
	}
	return res, nil
}

// IsLoggedIn encodes the domain logic for determining whether we're
// logged in. This might check our config storage, or just in memory.
func (a *Auth0) IsLoggedIn() bool {
	// No need to check errors for initializing context.
	_ = a.init()

	if a.system == "" {
		return false
	}

	// Parse the access token for the system.
	token, err := jwt.ParseString(a.config.Systems[a.system].AccessToken)
	if err != nil {
		return false
	}

	// Check if token is valid.
	// TODO: Choose issuer based on system
	if err = jwt.Validate(token, jwt.WithIssuer("https://vespa-cd.auth0.com/")); err != nil {
		return false
	}

	return true
}

// PrepareSystem loads the System, refreshing its token if necessary.
// The System access token needs a refresh if the access token has expired.
func (a *Auth0) PrepareSystem(ctx context.Context) (*System, error) {
	if err := a.init(); err != nil {
		return nil, err
	}
	s, err := a.getSystem()
	if err != nil {
		return nil, err
	}

	if s.AccessToken == "" {
		return nil, fmt.Errorf("access token missing: re-authenticate with 'vespa auth login'")
	} else if scopesChanged(s) {
		return nil, fmt.Errorf("authentication scopes cahnges: re-authenticate with 'vespa auth login'")
	} else if isExpired(s.ExpiresAt, accessTokenExpThreshold) {
		// check if the stored access token is expired:
		// use the refresh token to get a new access token:
		tr := &auth.TokenRetriever{
			Authenticator: a.Authenticator,
			Secrets:       &auth.Keyring{},
			Client:        http.DefaultClient,
		}

		res, err := tr.Refresh(ctx, a.system)
		if err != nil {
			return nil, fmt.Errorf("failed to renew access token: %w: %s", err, "re-authenticate with 'vespa auth login'")
		} else {
			// persist the updated system with renewed access token
			s.AccessToken = res.AccessToken
			s.ExpiresAt = time.Now().Add(
				time.Duration(res.ExpiresIn) * time.Second,
			)

			err = a.AddSystem(s)
			if err != nil {
				return nil, err
			}
		}
	}

	return s, nil
}

// isExpired is true if now() + a threshold is after the given date
func isExpired(t time.Time, threshold time.Duration) bool {
	return time.Now().Add(threshold).After(t)
}

// scopesChanged compare the System scopes
// with the currently required scopes.
func scopesChanged(s *System) bool {
	want := auth.RequiredScopes()
	got := s.Scopes

	sort.Strings(want)
	sort.Strings(got)

	if (want == nil) != (got == nil) {
		return true
	}

	if len(want) != len(got) {
		return true
	}

	for i := range s.Scopes {
		if want[i] != got[i] {
			return true
		}
	}

	return false
}

func (a *Auth0) getSystem() (*System, error) {
	if err := a.init(); err != nil {
		return nil, err
	}

	s, ok := a.config.Systems[a.system]
	if !ok {
		return nil, fmt.Errorf("unable to find system: %s; run 'vespa auth login' to configure a new system", a.system)
	}

	return s, nil
}

// HasSystem checks if the system is configured
// TODO: Used to print deprecation warning if we fall back to use tenant API key.
//       Remove when this is not longer needed.
func (a *Auth0) HasSystem() bool {
	if _, err := a.getSystem(); err != nil {
		return false
	}
	return true
}

// AddSystem assigns an existing, or new System. This is expected to be called
// after a login has completed.
func (a *Auth0) AddSystem(s *System) error {
	_ = a.init()

	// If we're dealing with an empty file, we'll need to initialize this map.
	if a.config.Systems == nil {
		a.config.Systems = map[string]*System{}
	}

	a.config.Systems[a.system] = s

	if err := a.persistConfig(); err != nil {
		return fmt.Errorf("unexpected error persisting config: %w", err)
	}

	return nil
}

func (a *Auth0) RemoveSystem(s string) error {
	_ = a.init()

	// If we're dealing with an empty file, we'll need to initialize this map.
	if a.config.Systems == nil {
		a.config.Systems = map[string]*System{}
	}

	delete(a.config.Systems, s)

	if err := a.persistConfig(); err != nil {
		return fmt.Errorf("unexpected error persisting config: %w", err)
	}

	tr := &auth.TokenRetriever{Secrets: &auth.Keyring{}}
	if err := tr.Delete(s); err != nil {
		return fmt.Errorf("unexpected error clearing system information: %w", err)
	}

	return nil
}

func (a *Auth0) persistConfig() error {
	dir := filepath.Dir(a.Path)
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		if err := os.MkdirAll(dir, 0700); err != nil {
			return err
		}
	}

	buf, err := a.configToJson(&a.config)
	if err != nil {
		return err
	}

	if err := os.WriteFile(a.Path, buf, 0600); err != nil {
		return err
	}

	return nil
}

func (a *Auth0) configToJson(cfg *config) ([]byte, error) {
	cfg.Version = 1
	r := configJsonFormat{
		Version: 1,
		Providers: providers{
			Config: *cfg,
		},
	}
	return json.MarshalIndent(r, "", "    ")
}

func (a *Auth0) jsonToConfig(buf []byte) (*config, error) {
	r := configJsonFormat{}
	if err := json.Unmarshal(buf, &r); err != nil {
		return nil, err
	}
	cfg := r.Providers.Config
	if cfg.Systems != nil {
		for n, s := range cfg.Systems {
			s.Name = n
		}
	}
	return &cfg, nil
}

func (a *Auth0) init() error {
	a.initOnce.Do(func() {
		if a.errOnce = a.initContext(); a.errOnce != nil {
			return
		}
	})
	return a.errOnce
}

func (a *Auth0) initContext() (err error) {
	if _, err := os.Stat(a.Path); os.IsNotExist(err) {
		return errUnauthenticated
	}

	var buf []byte
	if buf, err = os.ReadFile(a.Path); err != nil {
		return err
	}

	cfg, err := a.jsonToConfig(buf)
	if err != nil {
		return err
	}
	a.config = *cfg
	return nil
}
