// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package cli

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/joeshaw/envdecode"
	"github.com/pkg/browser"
	"github.com/vespa-engine/vespa/client/go/util"
	"io/ioutil"
	"net/http"
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

var errUnauthenticated = errors.New("not logged in. Try 'vespa login'")

type config struct {
	InstallID     string            `json:"install_id,omitempty"`
	DefaultTenant string            `json:"default_tenant"`
	Tenants       map[string]Tenant `json:"tenants"`
}

// Tenant is an auth0 Tenant.
type Tenant struct {
	Name         string    `json:"name"`
	Domain       string    `json:"domain"`
	AccessToken  string    `json:"access_token,omitempty"`
	Scopes       []string  `json:"scopes,omitempty"`
	ExpiresAt    time.Time `json:"expires_at"`
	ClientID     string    `json:"client_id"`
	ClientSecret string    `json:"client_secret"`
}

type Cli struct {
	Authenticator *auth.Authenticator
	tenant        string
	initOnce      sync.Once
	errOnce       error
	Path          string
	config        config
}

// IsLoggedIn encodes the domain logic for determining whether we're
// logged in. This might check our config storage, or just in memory.
func (c *Cli) IsLoggedIn() bool {
	// No need to check errors for initializing context.
	_ = c.init()

	if c.tenant == "" {
		return false
	}

	// Parse the access token for the tenant.
	t, err := jwt.ParseString(c.config.Tenants[c.tenant].AccessToken)
	if err != nil {
		return false
	}

	// Check if token is valid.
	if err = jwt.Validate(t, jwt.WithIssuer("https://vespa-cd.auth0.com/")); err != nil {
		return false
	}

	return true
}

// default to vespa-cd.auth0.com
var (
	authCfg struct {
		Audience           string `env:"AUTH0_AUDIENCE,default=https://vespa-cd.auth0.com/api/v2/"`
		ClientID           string `env:"AUTH0_CLIENT_ID,default=4wYWA496zBP28SLiz0PuvCt8ltL11DZX"`
		DeviceCodeEndpoint string `env:"AUTH0_DEVICE_CODE_ENDPOINT,default=https://vespa-cd.auth0.com/oauth/device/code"`
		OauthTokenEndpoint string `env:"AUTH0_OAUTH_TOKEN_ENDPOINT,default=https://vespa-cd.auth0.com/oauth/token"`
	}
)

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

// Setup will try to initialize the config context, as well as figure out if
// there's a readily available tenant.
func GetCli(configPath string) (*Cli, error) {
	c := Cli{}
	c.Path = configPath
	if err := envdecode.StrictDecode(&authCfg); err != nil {
		return nil, fmt.Errorf("could not decode env: %w", err)
	}
	c.Authenticator = &auth.Authenticator{
		Audience:           authCfg.Audience,
		ClientID:           authCfg.ClientID,
		DeviceCodeEndpoint: authCfg.DeviceCodeEndpoint,
		OauthTokenEndpoint: authCfg.OauthTokenEndpoint,
	}
	return &c, nil
}

// prepareTenant loads the Tenant, refreshing its token if necessary.
// The Tenant access token needs a refresh if:
// 1. the Tenant scopes are different from the currently required scopes.
// 2. the access token is expired.
func (c *Cli) PrepareTenant(ctx context.Context) (Tenant, error) {
	if err := c.init(); err != nil {
		return Tenant{}, err
	}
	t, err := c.getTenant()
	if err != nil {
		return Tenant{}, err
	}

	if t.ClientID != "" && t.ClientSecret != "" {
		return t, nil
	}

	if t.AccessToken == "" || scopesChanged(t) {
		t, err = RunLogin(ctx, c, true)
		if err != nil {
			return Tenant{}, err
		}
	} else if isExpired(t.ExpiresAt, accessTokenExpThreshold) {
		// check if the stored access token is expired:
		// use the refresh token to get a new access token:
		tr := &auth.TokenRetriever{
			Authenticator: c.Authenticator,
			Secrets:       &auth.Keyring{},
			Client:        http.DefaultClient,
		}

		res, err := tr.Refresh(ctx, t.Domain)
		if err != nil {
			// ask and guide the user through the login process:
			fmt.Println(fmt.Errorf("failed to renew access token, %s", err))
			t, err = RunLogin(ctx, c, true)
			if err != nil {
				return Tenant{}, err
			}
		} else {
			// persist the updated tenant with renewed access token
			t.AccessToken = res.AccessToken
			t.ExpiresAt = time.Now().Add(
				time.Duration(res.ExpiresIn) * time.Second,
			)

			err = c.AddTenant(t)
			if err != nil {
				return Tenant{}, err
			}
		}
	}

	return t, nil
}

// isExpired is true if now() + a threshold is after the given date
func isExpired(t time.Time, threshold time.Duration) bool {
	return time.Now().Add(threshold).After(t)
}

// scopesChanged compare the Tenant scopes
// with the currently required scopes.
func scopesChanged(t Tenant) bool {
	want := auth.RequiredScopes()
	got := t.Scopes

	sort.Strings(want)
	sort.Strings(got)

	if (want == nil) != (got == nil) {
		return true
	}

	if len(want) != len(got) {
		return true
	}

	for i := range t.Scopes {
		if want[i] != got[i] {
			return true
		}
	}

	return false
}

func (c *Cli) getTenant() (Tenant, error) {
	if err := c.init(); err != nil {
		return Tenant{}, err
	}

	t, ok := c.config.Tenants[c.tenant]
	if !ok {
		return Tenant{}, fmt.Errorf("unable to find tenant: %s; run 'vespa login' to configure a new tenant", c.tenant)
	}

	return t, nil
}

// AddTenant assigns an existing, or new Tenant. This is expected to be called
// after a login has completed.
func (c *Cli) AddTenant(ten Tenant) error {
	_ = c.init()

	if c.config.DefaultTenant == "" {
		c.config.DefaultTenant = ten.Domain
	}

	// If we're dealing with an empty file, we'll need to initialize this map.
	if c.config.Tenants == nil {
		c.config.Tenants = map[string]Tenant{}
	}

	c.config.Tenants[ten.Domain] = ten

	if err := c.persistConfig(); err != nil {
		return fmt.Errorf("unexpected error persisting config: %w", err)
	}

	return nil
}

func (c *Cli) persistConfig() error {
	dir := filepath.Dir(c.Path)
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		if err := os.MkdirAll(dir, 0700); err != nil {
			return err
		}
	}

	buf, err := json.MarshalIndent(c.config, "", "    ")
	if err != nil {
		return err
	}

	if err := ioutil.WriteFile(c.Path, buf, 0600); err != nil {
		return err
	}

	return nil
}

func (c *Cli) init() error {
	c.initOnce.Do(func() {
		if c.errOnce = c.initContext(); c.errOnce != nil {
			return
		}
	})
	return c.errOnce
}

func (c *Cli) initContext() (err error) {
	if _, err := os.Stat(c.Path); os.IsNotExist(err) {
		return errUnauthenticated
	}

	var buf []byte
	if buf, err = ioutil.ReadFile(c.Path); err != nil {
		return err
	}

	if err := json.Unmarshal(buf, &c.config); err != nil {
		return err
	}

	if c.tenant == "" && c.config.DefaultTenant == "" {
		return errUnauthenticated
	}

	if c.tenant == "" {
		c.tenant = c.config.DefaultTenant
	}

	return nil
}

// RunLogin runs the login flow guiding the user through the process
// by showing the login instructions, opening the browser.
// Use `expired` to run the login from other commands setup:
// this will only affect the messages.
func RunLogin(ctx context.Context, cli *Cli, expired bool) (Tenant, error) {
	if expired {
		fmt.Println("Please sign in to re-authorize the CLI.")
	}

	state, err := cli.Authenticator.Start(ctx)
	if err != nil {
		return Tenant{}, fmt.Errorf("could not start the authentication process: %w", err)
	}

	fmt.Printf("Your Device Confirmation code is: %s\n\n", state.UserCode)
	fmt.Println("Press Enter to open the browser to log in or ^C to quit...")
	fmt.Scanln()

	err = browser.OpenURL(state.VerificationURI)

	if err != nil {
		fmt.Printf("Couldn't open the URL, please do it manually: %s.", state.VerificationURI)
	}

	var res auth.Result
	err = util.Spinner("Waiting for login to complete in browser", func() error {
		res, err = cli.Authenticator.Wait(ctx, state)
		return err
	})

	if err != nil {
		return Tenant{}, fmt.Errorf("login error: %w", err)
	}

	fmt.Print("\n")
	fmt.Println("Successfully logged in.")
	fmt.Print("\n")

	// store the refresh token
	secretsStore := &auth.Keyring{}
	err = secretsStore.Set(auth.SecretsNamespace, res.Domain, res.RefreshToken)
	if err != nil {
		// log the error but move on
		fmt.Println("Could not store the refresh token locally, please expect to login again once your access token expired.")
	}

	t := Tenant{
		Name:        res.Tenant,
		Domain:      res.Domain,
		AccessToken: res.AccessToken,
		ExpiresAt:   time.Now().Add(time.Duration(res.ExpiresIn) * time.Second),
		Scopes:      auth.RequiredScopes(),
	}
	err = cli.AddTenant(t)
	if err != nil {
		return Tenant{}, fmt.Errorf("could not add tenant to config: %w", err)
	}

	return t, nil
}
