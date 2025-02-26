package vespa

import (
	"testing"
)

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
