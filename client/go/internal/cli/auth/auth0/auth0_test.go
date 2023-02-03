package auth0

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestConfigWriting(t *testing.T) {
	configPath := filepath.Join(t.TempDir(), "config")
	httpClient := mock.HTTPClient{}
	flowConfigResponse := `{
  "audience": "https://example.com/api/v2/",
  "client-id": "some-id",
  "device-code-endpoint": "https://example.com/oauth/device/code",
  "oauth-token-endpoint": "https://example.com/oauth/token"
}`
	httpClient.NextResponseString(200, flowConfigResponse)
	client, err := newClient(&httpClient, configPath, "public", "http://example.com")
	require.Nil(t, err)
	assert.Equal(t, "https://example.com/api/v2/", client.Authenticator.Audience)
	assert.Equal(t, "some-id", client.Authenticator.ClientID)
	assert.Equal(t, "https://example.com/oauth/device/code", client.Authenticator.DeviceCodeEndpoint)
	assert.Equal(t, "https://example.com/oauth/token", client.Authenticator.OauthTokenEndpoint)

	creds1 := Credentials{
		AccessToken: "some-token",
		Scopes:      []string{"foo", "bar"},
		ExpiresAt:   time.Date(2022, 03, 01, 15, 45, 50, 0, time.UTC),
	}
	require.Nil(t, client.WriteCredentials(creds1))
	expected := `{
    "version": 1,
    "providers": {
        "auth0": {
            "version": 1,
            "systems": {
                "public": {
                    "access_token": "some-token",
                    "scopes": [
                        "foo",
                        "bar"
                    ],
                    "expires_at": "2022-03-01T15:45:50Z"
                }
            }
        }
    }
}`
	assertConfig(t, expected, configPath)

	// Switch to another system
	httpClient.NextResponseString(200, flowConfigResponse)
	client, err = newClient(&httpClient, configPath, "publiccd", "http://example.com")
	require.Nil(t, err)
	creds2 := Credentials{
		AccessToken: "another-token",
		Scopes:      []string{"baz"},
		ExpiresAt:   time.Date(2022, 03, 01, 15, 45, 50, 0, time.UTC),
	}
	require.Nil(t, client.WriteCredentials(creds2))
	expected = `{
    "version": 1,
    "providers": {
        "auth0": {
            "version": 1,
            "systems": {
                "public": {
                    "access_token": "some-token",
                    "scopes": [
                        "foo",
                        "bar"
                    ],
                    "expires_at": "2022-03-01T15:45:50Z"
                },
                "publiccd": {
                    "access_token": "another-token",
                    "scopes": [
                        "baz"
                    ],
                    "expires_at": "2022-03-01T15:45:50Z"
                }
            }
        }
    }
}`
	assertConfig(t, expected, configPath)
}

func assertConfig(t *testing.T, expected, path string) {
	data, err := os.ReadFile(path)
	require.Nil(t, err)
	assert.Equal(t, expected, string(data))
}
