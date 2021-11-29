// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth0

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"github.com/lestrrat-go/jwx/jwt"
	"github.com/pkg/browser"
	"github.com/vespa-engine/vespa/client/go/auth"
	"github.com/vespa-engine/vespa/client/go/util"
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
	Version int               `json:"version"`
	Systems map[string]System `json:"systems"`
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
	if err = jwt.Validate(token, jwt.WithIssuer("https://vespa-cd.auth0.com/")); err != nil {
		return false
	}

	return true
}

// PrepareSystem loads the System, refreshing its token if necessary.
// The System access token needs a refresh if:
// 1. the System scopes are different from the currently required scopes - (auth0 changes).
// 2. the access token is expired.
func (a *Auth0) PrepareSystem(ctx context.Context) (System, error) {
	if err := a.init(); err != nil {
		return System{}, err
	}
	s, err := a.getSystem()
	if err != nil {
		return System{}, err
	}

	if s.AccessToken == "" || scopesChanged(s) {
		s, err = RunLogin(ctx, a, true)
		if err != nil {
			return System{}, err
		}
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
			// ask and guide the user through the login process:
			fmt.Println(fmt.Errorf("failed to renew access token, %s", err))
			fmt.Print("\n")
			s, err = RunLogin(ctx, a, true)
			if err != nil {
				return System{}, err
			}
		} else {
			// persist the updated system with renewed access token
			s.AccessToken = res.AccessToken
			s.ExpiresAt = time.Now().Add(
				time.Duration(res.ExpiresIn) * time.Second,
			)

			err = a.AddSystem(s)
			if err != nil {
				return System{}, err
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
func scopesChanged(s System) bool {
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

func (a *Auth0) getSystem() (System, error) {
	if err := a.init(); err != nil {
		return System{}, err
	}

	s, ok := a.config.Systems[a.system]
	if !ok {
		return System{}, fmt.Errorf("unable to find system: %s; run 'vespa auth login' to configure a new system", a.system)
	}

	return s, nil
}

// AddSystem assigns an existing, or new System. This is expected to be called
// after a login has completed.
func (a *Auth0) AddSystem(s System) error {
	_ = a.init()

	// If we're dealing with an empty file, we'll need to initialize this map.
	if a.config.Systems == nil {
		a.config.Systems = map[string]System{}
	}

	a.config.Systems[a.system] = s

	if err := a.persistConfig(); err != nil {
		return fmt.Errorf("unexpected error persisting config: %w", err)
	}

	return nil
}

func (a *Auth0) removeSystem(s string) error {
	_ = a.init()

	// If we're dealing with an empty file, we'll need to initialize this map.
	if a.config.Systems == nil {
		a.config.Systems = map[string]System{}
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

	if err := ioutil.WriteFile(a.Path, buf, 0600); err != nil {
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
	systems := cfg.Systems
	if systems != nil {
		for n, s := range systems {
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
	if buf, err = ioutil.ReadFile(a.Path); err != nil {
		return err
	}

	cfg, err := a.jsonToConfig(buf)
	if err != nil {
		return err
	}
	a.config = *cfg
	return nil
}

// RunLogin runs the login flow guiding the user through the process
// by showing the login instructions, opening the browser.
// Use `expired` to run the login from other commands setup:
// this will only affect the messages.
func RunLogin(ctx context.Context, a *Auth0, expired bool) (System, error) {
	if expired {
		fmt.Println("Please sign in to re-authorize the CLI.")
	}

	state, err := a.Authenticator.Start(ctx)
	if err != nil {
		return System{}, fmt.Errorf("could not start the authentication process: %w", err)
	}

	fmt.Printf("Your Device Confirmation code is: %s\n\n", state.UserCode)
	fmt.Println("Press Enter to open the browser to log in or ^C to quit...")
	fmt.Scanln()

	err = browser.OpenURL(state.VerificationURI)

	if err != nil {
		fmt.Printf("Couldn't open the URL, please do it manually: %s.", state.VerificationURI)
	}

	var res auth.Result
	err = util.Spinner("Waiting for login to complete in browser ...", func() error {
		res, err = a.Authenticator.Wait(ctx, state)
		return err
	})

	if err != nil {
		return System{}, fmt.Errorf("login error: %w", err)
	}

	fmt.Print("\n")
	fmt.Println("Successfully logged in.")
	fmt.Print("\n")

	// store the refresh token
	secretsStore := &auth.Keyring{}
	err = secretsStore.Set(auth.SecretsNamespace, a.system, res.RefreshToken)
	if err != nil {
		// log the error but move on
		fmt.Println("Could not store the refresh token locally, please expect to login again once your access token expired.")
	}

	s := System{
		Name:        a.system,
		AccessToken: res.AccessToken,
		ExpiresAt:   time.Now().Add(time.Duration(res.ExpiresIn) * time.Second),
		Scopes:      auth.RequiredScopes(),
	}
	err = a.AddSystem(s)
	if err != nil {
		return System{}, fmt.Errorf("could not add system to config: %w", err)
	}

	return s, nil
}

func RunLogout(a *Auth0) error {
	s, err := a.getSystem()
	if err != nil {
		return err
	}

	if err := a.removeSystem(s.Name); err != nil {
		return err
	}

	fmt.Print("\n")
	fmt.Println("Successfully logged out.")
	fmt.Print("\n")

	return nil
}
