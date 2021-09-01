// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCert(t *testing.T) {
	tmpDir := t.TempDir()
	mockApplicationPackage(t, tmpDir)
	out := execute(command{args: []string{"cert", "-a", "t1.a1.i1", tmpDir}, configDir: tmpDir}, t, nil)

	pkgCertificate := filepath.Join(tmpDir, "security", "clients.pem")
	certificate := filepath.Join(tmpDir, ".vespa", "t1.a1.i1", "data-plane-public-cert.pem")
	privateKey := filepath.Join(tmpDir, ".vespa", "t1.a1.i1", "data-plane-private-key.pem")

	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Certificate written to %s\nSuccess: Private key written to %s\n", pkgCertificate, certificate, privateKey), out)

	out = execute(command{args: []string{"cert", "-a", "t1.a1.i1", tmpDir}, configDir: tmpDir}, t, nil)
	assert.True(t, strings.HasPrefix(out, "Error: Certificate or private key"))
}

func mockApplicationPackage(t *testing.T, testDir string) {
	servicesXML := filepath.Join(testDir, "services.xml")
	if _, err := os.Create(servicesXML); err != nil {
		t.Fatal(err)
	}
}
