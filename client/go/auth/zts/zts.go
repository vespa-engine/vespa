package zts

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/util"
)

const DefaultURL = "https://zts.athenz.ouroath.com:4443"

// Client is a client for Athenz ZTS, an authentication token service.
type Client struct {
	client   util.HTTPClient
	tokenURL *url.URL
}

// NewClient creates a new client for an Athenz ZTS service located at serviceURL.
func NewClient(serviceURL string, client util.HTTPClient) (*Client, error) {
	tokenURL, err := url.Parse(serviceURL)
	if err != nil {
		return nil, err
	}
	tokenURL.Path = "/zts/v1/oauth2/token"
	return &Client{tokenURL: tokenURL, client: client}, nil
}

// AccessToken returns an access token within the given domain, using certificate to authenticate with ZTS.
func (c *Client) AccessToken(domain string, certificate tls.Certificate) (string, error) {
	data := fmt.Sprintf("grant_type=client_credentials&scope=%s:domain", domain)
	req, err := http.NewRequest("POST", c.tokenURL.String(), strings.NewReader(data))
	if err != nil {
		return "", err
	}
	c.client.UseCertificate([]tls.Certificate{certificate})
	response, err := c.client.Do(req, 10*time.Second)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return "", fmt.Errorf("got status %d from %s", response.StatusCode, c.tokenURL.String())
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
