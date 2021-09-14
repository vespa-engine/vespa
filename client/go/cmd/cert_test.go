// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func TestCert(t *testing.T) {
	homeDir := t.TempDir()
	pkgDir := mockApplicationPackage(t, false)
	out := execute(command{args: []string{"cert", "-a", "t1.a1.i1", pkgDir}, homeDir: homeDir}, t, nil)

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)

	appDir := filepath.Join(pkgDir, "src", "main", "application")
	pkgCertificate := filepath.Join(appDir, "security", "clients.pem")
	certificate := filepath.Join(homeDir, ".vespa", app.String(), "data-plane-public-cert.pem")
	privateKey := filepath.Join(homeDir, ".vespa", app.String(), "data-plane-private-key.pem")

	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Certificate written to %s\nSuccess: Private key written to %s\n", pkgCertificate, certificate, privateKey), out)

	out = execute(command{args: []string{"cert", "-a", "t1.a1.i1", pkgDir}, homeDir: homeDir}, t, nil)
	assert.Contains(t, out, fmt.Sprintf("Error: Application package %s already contains a certificate", appDir))
}

func TestCertCompressedPackage(t *testing.T) {
	homeDir := t.TempDir()
	pkgDir := mockApplicationPackage(t, true)
	zipFile := filepath.Join(pkgDir, "target", "application.zip")
	err := os.MkdirAll(filepath.Dir(zipFile), 0755)
	assert.Nil(t, err)
	_, err = os.Create(zipFile)
	assert.Nil(t, err)

	out := execute(command{args: []string{"cert", "-a", "t1.a1.i1", pkgDir}, homeDir: homeDir}, t, nil)
	assert.Contains(t, out, "Error: Cannot add certificate to compressed application package")

	err = os.Remove(zipFile)
	assert.Nil(t, err)

	out = execute(command{args: []string{"cert", "-f", "-a", "t1.a1.i1", pkgDir}, homeDir: homeDir}, t, nil)
	assert.Contains(t, out, "Success: Certificate written to")
	assert.Contains(t, out, "Success: Private key written to")
}

func mockApplicationPackage(t *testing.T, java bool) string {
	dir := t.TempDir()
	appDir := filepath.Join(dir, "src", "main", "application")
	if err := os.MkdirAll(appDir, 0755); err != nil {
		t.Fatal(err)
	}
	servicesXML := filepath.Join(appDir, "services.xml")
	if _, err := os.Create(servicesXML); err != nil {
		t.Fatal(err)
	}
	if java {
		if _, err := os.Create(filepath.Join(dir, "pom.xml")); err != nil {
			t.Fatal(err)
		}
	}
	return dir
}
