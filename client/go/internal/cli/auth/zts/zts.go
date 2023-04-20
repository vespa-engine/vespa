package zts

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

const DefaultURL = "https://zts.athenz.ouroath.com:4443"

// Client is a client for Athenz ZTS, an authentication token service.
type Client struct {
	client   util.HTTPClient
	tokenURL *url.URL
	domain   string
}

// NewClient creates a new client for an Athenz ZTS service located at serviceURL.
func NewClient(client util.HTTPClient, domain, serviceURL string) (*Client, error) {
	tokenURL, err := url.Parse(serviceURL)
	if err != nil {
		return nil, err
	}
	tokenURL.Path = "/zts/v1/oauth2/token"
	return &Client{tokenURL: tokenURL, client: client, domain: domain}, nil
}

func (c *Client) Authenticate(request *http.Request) error {
	accessToken, err := c.AccessToken()
	if err != nil {
		return err
	}
	if request.Header == nil {
		request.Header = make(http.Header)
	}
	request.Header.Add("Authorization", "Bearer "+accessToken)
	return nil
}

// AccessToken returns an access token within the domain configured in client c.
func (c *Client) AccessToken() (string, error) {
	// TODO(mpolden): This should cache and re-use tokens until expiry
	data := fmt.Sprintf("grant_type=client_credentials&scope=%s:domain", c.domain)
	req, err := http.NewRequest("POST", c.tokenURL.String(), strings.NewReader(data))
	if err != nil {
		return "", err
	}
	response, err := c.client.Do(req, 10*time.Second)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return "", fmt.Errorf("zts: got status %d from %s", response.StatusCode, c.tokenURL.String())
	}
	var ztsResponse struct {
		AccessToken string `json:"access_token"`
	}
	dec := json.NewDecoder(response.Body)
	if err := dec.Decode(&ztsResponse); err != nil {
		return "", err
	}
	return ztsResponse.AccessToken, nil
}
