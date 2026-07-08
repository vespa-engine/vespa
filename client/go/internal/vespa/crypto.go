// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/md5"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
)

const (
	certificateExpiry = 3650 * 24 * time.Hour // Approximately 10 years
)

func defaultCommonName() string {
	user := os.Getenv("USER")
	if user == "" {
		user = os.Getenv("LOGNAME")
	}
	if user == "" {
		user = "unknown"
	}
	hostname, err := os.Hostname()
	if err != nil {
		hostname = "unknown"
	}
	return user + "@" + hostname
}

// PemKeyPair represents a PEM-encoded private key and X509 certificate.
type PemKeyPair struct {
	Certificate []byte
	PrivateKey  []byte
}

// WriteCertificateFile writes the certificate contained in this key pair to certificateFile.
func (kp *PemKeyPair) WriteCertificateFile(certificateFile string, overwrite bool) error {
	if ioutil.Exists(certificateFile) && !overwrite {
		return fmt.Errorf("cannot overwrite existing file: %s", certificateFile)
	}
	return ioutil.AtomicWriteFile(certificateFile, kp.Certificate)
}

// WritePrivateKeyFile writes the private key contained in this key pair to privateKeyFile.
func (kp *PemKeyPair) WritePrivateKeyFile(privateKeyFile string, overwrite bool) error {
	if ioutil.Exists(privateKeyFile) && !overwrite {
		return fmt.Errorf("cannot overwrite existing file: %s", privateKeyFile)
	}
	return ioutil.AtomicWriteFile(privateKeyFile, kp.PrivateKey)
}

// CreateKeyPair creates a key pair containing a private key and self-signed X509 certificate.
func CreateKeyPair() (PemKeyPair, error) {
	privateKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return PemKeyPair{}, fmt.Errorf("failed to generate private key: %w", err)
	}
	serialNumber, err := randomSerialNumber()
	if err != nil {
		return PemKeyPair{}, fmt.Errorf("failed to create serial number: %w", err)
	}
	notBefore := time.Now()
	notAfter := notBefore.Add(certificateExpiry)
	template := x509.Certificate{
		SerialNumber: serialNumber,
		Subject:      pkix.Name{CommonName: defaultCommonName()},
		NotBefore:    notBefore,
		NotAfter:     notAfter,
	}
	certificateDER, err := x509.CreateCertificate(rand.Reader, &template, &template, &privateKey.PublicKey, privateKey)
	if err != nil {
		return PemKeyPair{}, err
	}
	privateKeyDER, err := x509.MarshalPKCS8PrivateKey(privateKey)
	if err != nil {
		return PemKeyPair{}, err
	}
	pemPrivateKey := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: privateKeyDER})
	pemCertificate := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: certificateDER})
	return PemKeyPair{Certificate: pemCertificate, PrivateKey: pemPrivateKey}, nil
}

// CreateAPIKey creates a EC private key encoded as PEM
func CreateAPIKey() ([]byte, error) {
	privateKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("failed to generate private key: %w", err)
	}
	privateKeyDER, err := x509.MarshalECPrivateKey(privateKey)
	if err != nil {
		return nil, err
	}
	return pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: privateKeyDER}), nil
}

type RequestSigner struct {
	now           func() time.Time
	rnd           io.Reader
	KeyID         string
	PemPrivateKey []byte
}

// NewRequestSigner creates a new signer using the EC pemPrivateKey. keyID names the key used to sign requests.
func NewRequestSigner(keyID string, pemPrivateKey []byte) *RequestSigner {
	return &RequestSigner{
		now:           time.Now,
		rnd:           rand.Reader,
		KeyID:         keyID,
		PemPrivateKey: pemPrivateKey,
	}
}

func (rs *RequestSigner) Authenticate(request *http.Request) error { return rs.SignRequest(request) }

// SignRequest signs the given HTTP request using the private key in rs
func (rs *RequestSigner) SignRequest(request *http.Request) error {
	timestamp := rs.now().UTC().Format(time.RFC3339)
	contentHash, body, err := contentHash(request.Body)
	if err != nil {
		return err
	}
	privateKey, err := ECPrivateKeyFrom(rs.PemPrivateKey)
	if err != nil {
		return err
	}
	pemPublicKey, err := PEMPublicKeyFrom(privateKey)
	if err != nil {
		return err
	}
	base64PemPublicKey := base64.StdEncoding.EncodeToString(pemPublicKey)
	signature, err := rs.hashAndSign(privateKey, request, timestamp, contentHash)
	if err != nil {
		return err
	}
	base64Signature := base64.StdEncoding.EncodeToString(signature)
	request.Body = io.NopCloser(body)
	if request.Header == nil {
		request.Header = make(http.Header)
	}
	request.Header.Set("X-Timestamp", timestamp)
	request.Header.Set("X-Content-Hash", contentHash)
	request.Header.Set("X-Key-Id", rs.KeyID)
	request.Header.Set("X-Key", base64PemPublicKey)
	request.Header.Set("X-Authorization", base64Signature)
	return nil
}

func (rs *RequestSigner) hashAndSign(privateKey *ecdsa.PrivateKey, request *http.Request, timestamp, contentHash string) ([]byte, error) {
	msg := []byte(request.Method + "\n" + request.URL.String() + "\n" + timestamp + "\n" + contentHash)
	hasher := sha256.New()
	hasher.Write(msg)
	hash := hasher.Sum(nil)
	return ecdsa.SignASN1(rs.rnd, privateKey, hash)
}

// ECPrivateKeyFrom reads an EC private key (in raw or PKCS8 format) from the PEM-encoded pemPrivateKey.
func ECPrivateKeyFrom(pemPrivateKey []byte) (*ecdsa.PrivateKey, error) {
	privateKeyBlock, _ := pem.Decode(pemPrivateKey)
	if privateKeyBlock == nil {
		return nil, fmt.Errorf("invalid pem private key")
	}
	if privateKeyBlock.Type == "EC PRIVATE KEY" {
		privateKey, err := x509.ParseECPrivateKey(privateKeyBlock.Bytes) // Raw EC private key
		if err != nil {
			return nil, fmt.Errorf("invalid raw ec private key: %w", err)
		}
		return privateKey, nil
	}
	privateKey, err := x509.ParsePKCS8PrivateKey(privateKeyBlock.Bytes) // Try PKCS8 format
	if err != nil {
		return nil, fmt.Errorf("invalid pkcs8 private key: %w", err)
	}
	ecKey, ok := privateKey.(*ecdsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("invalid private key type: %T", ecKey)
	}
	return ecKey, nil
}

// PEMPublicKeyFrom extracts the public key from privateKey encoded as PEM.
func PEMPublicKeyFrom(privateKey *ecdsa.PrivateKey) ([]byte, error) {
	publicKeyDER, err := x509.MarshalPKIXPublicKey(privateKey.Public())
	if err != nil {
		return nil, err
	}
	return pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: publicKeyDER}), nil
}

// FingerprintMD5 returns a MD5 fingerprint of publicKey.
func FingerprintMD5(pemPublicKey []byte) (string, error) {
	publicKeyDER, _ := pem.Decode(pemPublicKey)
	if publicKeyDER == nil {
		return "", fmt.Errorf("invalid pem data")
	}
	md5sum := md5.Sum(publicKeyDER.Bytes)
	hexDigits := make([]string, len(md5sum))
	for i, c := range md5sum {
		hexDigits[i] = hex.EncodeToString([]byte{c})
	}
	return strings.Join(hexDigits, ":"), nil
}

func contentHash(r io.Reader) (string, io.Reader, error) {
	if r == nil {
		r = strings.NewReader("") // Request without body
	}
	var copy bytes.Buffer
	teeReader := io.TeeReader(r, &copy) // Copy reader contents while we hash it
	hasher := sha256.New()
	if _, err := io.Copy(hasher, teeReader); err != nil {
		return "", nil, err
	}
	hashSum := hasher.Sum(nil)
	return base64.StdEncoding.EncodeToString(hashSum), &copy, nil
}

func randomSerialNumber() (*big.Int, error) {
	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	return rand.Int(rand.Reader, serialNumberLimit)
}

// isTLSAlert returns whether err contains a TLS alert error.
func isTLSAlert(err error) bool {
	for ; err != nil; err = errors.Unwrap(err) {
		// This is ugly, but alert types are currently not exposed:
		// https://github.com/golang/go/issues/35234
		if fmt.Sprintf("%T", err) == "tls.alert" {
			return true
		}
	}
	return false
}
