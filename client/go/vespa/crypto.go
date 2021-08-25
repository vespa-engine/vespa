package vespa

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"errors"
	"fmt"
	"math/big"
	"os"
	"time"
)

const (
	defaultCommonName = "cloud.vespa.example"
	certificateExpiry = 3650 * 24 * time.Hour // Approximately 10 years
	tempFilePattern   = "vespa"
)

// PemKeyPair represents a PEM-encoded private key and X509 certificate.
type PemKeyPair struct {
	Certificate []byte
	PrivateKey  []byte
}

// WriteCertificateFile writes the certificate contained in this key pair to certificateFile.
func (kp *PemKeyPair) WriteCertificateFile(certificateFile string, overwrite bool) error {
	return atomicWriteFile(certificateFile, kp.Certificate, overwrite)
}

// WritePrivateKeyFile writes the private key contained in this key pair to privateKeyFile.
func (kp *PemKeyPair) WritePrivateKeyFile(privateKeyFile string, overwrite bool) error {
	return atomicWriteFile(privateKeyFile, kp.PrivateKey, overwrite)
}

func atomicWriteFile(filename string, data []byte, overwrite bool) error {
	tmpFile, err := os.CreateTemp("", tempFilePattern)
	if err != nil {
		return err
	}
	defer os.Remove(tmpFile.Name())
	if err := os.WriteFile(tmpFile.Name(), data, 0600); err != nil {
		return err
	}
	_, err = os.Stat(filename)
	if errors.Is(err, os.ErrNotExist) || overwrite {
		return os.Rename(tmpFile.Name(), filename)
	}
	return fmt.Errorf("cannot overwrite existing file: %s", filename)
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
		Subject:      pkix.Name{CommonName: defaultCommonName},
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

func randomSerialNumber() (*big.Int, error) {
	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	return rand.Int(rand.Reader, serialNumberLimit)
}
