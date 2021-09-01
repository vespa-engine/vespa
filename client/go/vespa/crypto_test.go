package vespa

import (
	"encoding/base64"
	"math/rand"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestCreateKeyPair(t *testing.T) {
	kp, err := CreateKeyPair()
	assert.Nil(t, err)
	assert.NotEmpty(t, kp.Certificate)
	assert.NotEmpty(t, kp.PrivateKey)
}

func TestSignRequest(t *testing.T) {
	fixedTime := time.Unix(0, 0)
	rnd := rand.New(rand.NewSource(0)) // Fixed seed for testing purposes
	privateKey, err := CreateAPIKey()
	if err != nil {
		t.Fatal(err)
	}
	rs := RequestSigner{
		now:           func() time.Time { return fixedTime },
		rnd:           rnd,
		KeyID:         "my-key",
		PemPrivateKey: []byte(privateKey),
	}
	req, err := http.NewRequest("POST", "https://example.com", strings.NewReader("body"))
	if err != nil {
		assert.Nil(t, err)
	}

	if err := rs.SignRequest(req); err != nil {
		assert.Nil(t, err)
	}

	assert.Equal(t, "1970-01-01T00:00:00Z", req.Header.Get("X-Timestamp"))
	assert.Equal(t, "Iw2DWNyOiJC0xY3utikS7i8gNXrpKlzIYbmOaP4xrLU=", req.Header.Get("X-Content-Hash"))
	assert.Equal(t, "my-key", req.Header.Get("X-Key-Id"))
	key := req.Header.Get("X-Key")
	assert.NotEmpty(t, key)
	_, err = base64.StdEncoding.DecodeString(key)
	assert.Nil(t, err)
	auth := req.Header.Get("X-Authorization")
	assert.NotEmpty(t, auth)
	_, err = base64.StdEncoding.DecodeString(auth)
	assert.Nil(t, err)
}

func TestFingerprintMD5(t *testing.T) {
	pemData := []byte(`-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEObBhkEO6w1YwLXU441keCDGKe+f8
lu+CDhkxu4ZwLbwQtKBlNF5F7TXuTapUwcTErVgqrHqogrQUzthqrhbNfg==
-----END PUBLIC KEY-----`)
	fp, err := FingerprintMD5(pemData)
	if err != nil {
		t.Fatal(err)
	}
	assert.Equal(t, "c5:26:6a:11:e2:b5:74:f3:73:66:9d:80:2e:fd:b7:96", fp)
}
