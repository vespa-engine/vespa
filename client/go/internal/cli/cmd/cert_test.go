// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func TestCert(t *testing.T) {
	t.Run("auth cert", func(t *testing.T) {
		testCert(t, []string{"auth", "cert"})
	})
}

func testCert(t *testing.T, subcommand []string) {
	appDir, pkgDir := mock.ApplicationPackageDir(t, false, false)

	cli, stdout, stderr := newTestCLI(t)
	args := append(subcommand, "-a", "t1.a1.i1", pkgDir)
	err := cli.Run(args...)
	assert.Nil(t, err)

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)

	pkgCertificate := filepath.Join(appDir, "security", "clients.pem")
	homeDir := cli.config.homeDir
	certificate := filepath.Join(homeDir, app.String(), "data-plane-public-cert.pem")
	privateKey := filepath.Join(homeDir, app.String(), "data-plane-private-key.pem")

	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Certificate written to %s\nSuccess: Private key written to %s\n", pkgCertificate, certificate, privateKey), stdout.String())

	args = append(subcommand, "-a", "t1.a1.i1", pkgDir)
	err = cli.Run(args...)
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: application package %s already contains a certificate", appDir))
}

func TestCertCompressedPackage(t *testing.T) {
	t.Run("auth cert", func(t *testing.T) {
		testCertCompressedPackage(t, []string{"auth", "cert"})
	})
}

func testCertCompressedPackage(t *testing.T, subcommand []string) {
	_, pkgDir := mock.ApplicationPackageDir(t, true, false)
	zipFile := filepath.Join(pkgDir, "target", "application.zip")
	err := os.MkdirAll(filepath.Dir(zipFile), 0755)
	assert.Nil(t, err)
	_, err = os.Create(zipFile)
	assert.Nil(t, err)

	cli, stdout, stderr := newTestCLI(t)

	args := append(subcommand, "-a", "t1.a1.i1", pkgDir)
	err = cli.Run(args...)
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), "Error: cannot add certificate to compressed application package")

	err = os.Remove(zipFile)
	assert.Nil(t, err)

	args = append(subcommand, "-f", "-a", "t1.a1.i1", pkgDir)
	err = cli.Run(args...)
	assert.Nil(t, err)
	assert.Contains(t, stdout.String(), "Success: Certificate written to")
	assert.Contains(t, stdout.String(), "Success: Private key written to")
}

func TestCertAdd(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	err := cli.Run("auth", "cert", "-N", "-a", "t1.a1.i1")
	assert.Nil(t, err)

	appDir, pkgDir := mock.ApplicationPackageDir(t, false, false)
	stdout.Reset()
	err = cli.Run("auth", "cert", "add", "-a", "t1.a1.i1", pkgDir)
	assert.Nil(t, err)
	pkgCertificate := filepath.Join(appDir, "security", "clients.pem")
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\n", pkgCertificate), stdout.String())

	err = cli.Run("auth", "cert", "add", "-a", "t1.a1.i1", pkgDir)
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: application package %s already contains a certificate", appDir))
	stdout.Reset()
	err = cli.Run("auth", "cert", "add", "-f", "-a", "t1.a1.i1", pkgDir)
	assert.Nil(t, err)
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\n", pkgCertificate), stdout.String())
}

func TestCertNoAdd(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)

	err := cli.Run("auth", "cert", "-N", "-a", "t1.a1.i1")
	assert.Nil(t, err)
	homeDir := cli.config.homeDir

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)

	certificate := filepath.Join(homeDir, app.String(), "data-plane-public-cert.pem")
	privateKey := filepath.Join(homeDir, app.String(), "data-plane-private-key.pem")
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Private key written to %s\n", certificate, privateKey), stdout.String())

	err = cli.Run("auth", "cert", "-N", "-a", "t1.a1.i1")
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: private key %s already exists", privateKey))
	require.Nil(t, os.Remove(privateKey))

	stderr.Reset()
	err = cli.Run("auth", "cert", "-N", "-a", "t1.a1.i1")
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: certificate %s already exists", certificate))

	stdout.Reset()
	err = cli.Run("auth", "cert", "-N", "-f", "-a", "t1.a1.i1")
	assert.Nil(t, err)
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Private key written to %s\n", certificate, privateKey), stdout.String())
}
