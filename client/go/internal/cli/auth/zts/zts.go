package zts

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

const (
	DefaultURL  = "https://zts.athenz.ouroath.com:4443"
	expirySlack = 5 * time.Minute
)

// Client is a client for Athenz ZTS, an authentication token service.
type Client struct {
	client   util.HTTPClient
	tokenURL *url.URL
	domain   string
	now      func() time.Time
	token    Token
	mu       sync.Mutex
}

// Token is an access token retrieved from ZTS.
type Token struct {
	Value     string
	ExpiresAt time.Time
}

func (t *Token) isExpired(now time.Time) bool { return t.ExpiresAt.Sub(now) < expirySlack }

// NewClient creates a new client for an Athenz ZTS service located at serviceURL.
func NewClient(client util.HTTPClient, domain, serviceURL string) (*Client, error) {
	tokenURL, err := url.Parse(serviceURL)
	if err != nil {
		return nil, err
	}
	tokenURL.Path = "/zts/v1/oauth2/token"
	return &Client{tokenURL: tokenURL, client: client, domain: domain, now: time.Now}, nil
}

func (c *Client) Authenticate(request *http.Request) error {
	now := c.now()
	if c.token.isExpired(now) {
		c.mu.Lock()
		if c.token.isExpired(now) {
			accessToken, err := c.AccessToken()
			if err != nil {
				c.mu.Unlock()
				return err
			}
			c.token = accessToken
		}
		c.mu.Unlock()
	}
	if request.Header == nil {
		request.Header = make(http.Header)
	}
	request.Header.Add("Authorization", "Bearer "+c.token.Value)
	return nil
}

// AccessToken returns an access token within the domain configured in client c.
func (c *Client) AccessToken() (Token, error) {
	data := fmt.Sprintf("grant_type=client_credentials&scope=%s:domain", c.domain)
	req, err := http.NewRequest("POST", c.tokenURL.String(), strings.NewReader(data))
	if err != nil {
		return Token{}, err
	}
	now := c.now()
	response, err := c.client.Do(req, 10*time.Second)
	if err != nil {
		return Token{}, err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return Token{}, fmt.Errorf("zts: got status %d from %s", response.StatusCode, c.tokenURL.String())
	}
	var ztsResponse struct {
		AccessToken string `json:"access_token"`
		ExpirySecs  int    `json:"expires_in"`
	}
	b, err := io.ReadAll(response.Body)
	if err != nil {
		return Token{}, err
	}
	if err := json.Unmarshal(b, &ztsResponse); err != nil {
		return Token{}, err
	}
	return Token{
		Value:     ztsResponse.AccessToken,
		ExpiresAt: now.Add(time.Duration(ztsResponse.ExpirySecs) * time.Second),
	}, nil
}
