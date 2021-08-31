package vespa

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"io"
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
	privateKey := pemECPrivateKey(t, rnd)
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

func pemECPrivateKey(t *testing.T, rnd io.Reader) []byte {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rnd)
	if err != nil {
		t.Fatal(err)
	}
	der, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: der})
}
