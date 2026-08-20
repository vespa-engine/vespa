package vespa

import (
	"archive/zip"
	"os"
	"path/filepath"
	"testing"
)

const servicesXMLWithClientCertificate = `<services version="1.0">
  <container id="default" version="1.0">
    <clients>
      <client id="mtls" permissions="read,write">
        <certificate file="certs/search-pre.pem"/>
      </client>
    </clients>
  </container>
</services>`

func TestHasCertificateFromServicesXML(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "services.xml"), []byte(servicesXMLWithClientCertificate), 0o644); err != nil {
		t.Fatal(err)
	}
	// The declared certificate file exists at its own path, not under security/
	if err := os.MkdirAll(filepath.Join(dir, "certs"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "certs", "search-pre.pem"), []byte("certificate contents"), 0o644); err != nil {
		t.Fatal(err)
	}
	pkg, err := FindApplicationPackage(dir, PackageOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if !pkg.HasCertificate() {
		t.Error("expected HasCertificate to be true when services.xml declares a <clients> certificate")
	}
	if pkg.HasCertificateFile() {
		t.Error("expected HasCertificateFile to be false when security/clients.pem does not exist")
	}
	if pkg.clientsPath != "certs/search-pre.pem" {
		t.Errorf("expected clientsPath to be resolved to the declared certificate file, got %q", pkg.clientsPath)
	}
}

func TestHasCertificateFromServicesXMLInZip(t *testing.T) {
	dir := t.TempDir()
	zipPath := filepath.Join(dir, "app.zip")
	f, err := os.Create(zipPath)
	if err != nil {
		t.Fatal(err)
	}
	w := zip.NewWriter(f)
	fw, err := w.Create("services.xml")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := fw.Write([]byte(servicesXMLWithClientCertificate)); err != nil {
		t.Fatal(err)
	}
	// The declared certificate file exists in the zip too, at its own path
	certFw, err := w.Create("certs/search-pre.pem")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := certFw.Write([]byte("certificate contents")); err != nil {
		t.Fatal(err)
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}
	pkg, err := FindApplicationPackage(zipPath, PackageOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if !pkg.HasCertificate() {
		t.Error("expected HasCertificate to be true for a zipped package declaring a <clients> certificate")
	}
	if pkg.HasCertificateFile() {
		t.Error("expected HasCertificateFile to be false when security/clients.pem does not exist in the zip")
	}
	if pkg.clientsPath != "certs/search-pre.pem" {
		t.Errorf("expected clientsPath to be resolved to the declared certificate file, got %q", pkg.clientsPath)
	}
	if !pkg.hasFile("certs", "search-pre.pem") {
		t.Error("expected the declared certificate file to be found inside the zip")
	}
}

func TestHasCertificateWithoutClientsDeclaration(t *testing.T) {
	dir := t.TempDir()
	servicesXML := `<services version="1.0">
  <container id="default" version="1.0">
  </container>
</services>`
	if err := os.WriteFile(filepath.Join(dir, "services.xml"), []byte(servicesXML), 0o644); err != nil {
		t.Fatal(err)
	}
	pkg, err := FindApplicationPackage(dir, PackageOptions{})
	if err != nil {
		t.Fatal(err)
	}
	if pkg.HasCertificate() {
		t.Error("expected HasCertificate to be false without a clients.pem file or <clients> declaration")
	}
}

func TestPemEquality(t *testing.T) {
	pemA := `-----BEGIN CERTIFICATE-----
MIIBODCB3qADAgECAhAD8xeupfhJryA1goAXbZ+QMAoGCCqGSM49BAMCMB4xHDAa
BgNVBAMTE2Nsb3VkLnZlc3BhLmV4YW1wbGUwHhcNMjUwMjE3MTMyNjEwWhcNMzUw
MjE1MTMyNjEwWjAeMRwwGgYDVQQDExNjbG91ZC52ZXNwYS5leGFtcGxlMFkwEwYH
KoZIzj0CAQYIKoZIzj0DAQcDQgAEqZuffHm6VDI7kwvbvgLJK4MwY0HBxPGpUcX3
Wd2OXoaUyadgrb+cFqmBDFHUxmGYvkDSAdm3WXdww0RGFRHCkjAKBggqhkjOPQQD
AgNJADBGAiEA5rfRxchPjk3PeJy8dpYG6NkBYV2nQyghU3H98Yk+6ukCIQDFYuH2
F0DsRVefHok0LOaiiF6NQEzzxlvXpE789nupqg==
-----END CERTIFICATE-----`
	pemB := `-----BEGIN CERTIFICATE-----
MIIBOTCB36ADAgECAhEAr1LdmvSo8h8mEX+l2Mk6cjAKBggqhkjOPQQDAjAeMRww
GgYDVQQDExNjbG91ZC52ZXNwYS5leGFtcGxlMB4XDTI1MDEwNjA4MTAwNloXDTM1
MDEwNDA4MTAwNlowHjEcMBoGA1UEAxMTY2xvdWQudmVzcGEuZXhhbXBsZTBZMBMG
ByqGSM49AgEGCCqGSM49AwEHA0IABFDNmsfxKBGFc/0t/cYxUOKaUKVWIh5zMTmO
NsDDSv5nWR1hQOPUtTp44rtgKKh+zl8ZdrrPXu8ejhEg+yUpue8wCgYIKoZIzj0E
AwIDSQAwRgIhAPoe2ayRJ/rg1cM69DDKuQ/IgZY2rnAVqa1Tl0CUnOAlAiEA48WI
sLEZh2u4owcwLcw3Bqn5pnIFlGla4oZd7nzUBBg=
-----END CERTIFICATE-----`

	tests := []struct {
		name         string
		certificates string
		expected     bool
	}{
		{
			name:         "simple",
			certificates: pemA,
			expected:     true,
		},
		{
			name:         "simple",
			certificates: pemB,
			expected:     false,
		},
		{
			name:         "multiple",
			certificates: pemB + "\n" + pemA,
			expected:     true,
		},
		{
			name:         "comment",
			certificates: "# Alice's client certificate\n" + pemA,
			expected:     true,
		},
	}

	// Run all test cases
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, _ := containsMatchingCertificate([]byte(pemA), []byte(tt.certificates))
			if got != tt.expected {
				if got {
					t.Errorf("Expected certificate to be part of clients set, but it wasn't")
				} else {
					t.Errorf("Did not expect certificate to be part of clients set, but it was")
				}
			}
		})
	}
}
