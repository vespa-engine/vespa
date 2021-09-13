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
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func TestCert(t *testing.T) {
	homeDir := t.TempDir()
	mockApplicationPackage(t, homeDir)
	out := execute(command{args: []string{"cert", "-a", "t1.a1.i1", homeDir}, homeDir: homeDir}, t, nil)

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)
	pkgCertificate := filepath.Join(homeDir, "security", "clients.pem")
	certificate := filepath.Join(homeDir, ".vespa", app.String(), "data-plane-public-cert.pem")
	privateKey := filepath.Join(homeDir, ".vespa", app.String(), "data-plane-private-key.pem")

	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Certificate written to %s\nSuccess: Private key written to %s\n", pkgCertificate, certificate, privateKey), out)

	out = execute(command{args: []string{"cert", "-a", "t1.a1.i1", homeDir}, homeDir: homeDir}, t, nil)
	assert.True(t, strings.HasPrefix(out, "Error: Certificate or private key"))
}

func mockApplicationPackage(t *testing.T, testDir string) {
	servicesXML := filepath.Join(testDir, "services.xml")
	if _, err := os.Create(servicesXML); err != nil {
		t.Fatal(err)
	}
}
