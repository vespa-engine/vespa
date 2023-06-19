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

func configureCloud(t *testing.T, cli *CLI) {
	require.Nil(t, cli.Run("config", "set", "application", "t1.a1.i1"))
	require.Nil(t, cli.Run("config", "set", "target", "cloud"))
	require.Nil(t, cli.Run("auth", "api-key"))
}

func testCert(t *testing.T, subcommand []string) {
	appDir, pkgDir := mock.ApplicationPackageDir(t, false, false)

	cli, stdout, _ := newTestCLI(t)
	configureCloud(t, cli)
	stdout.Reset()

	args := append(subcommand, pkgDir)
	require.Nil(t, cli.Run(args...))

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)

	pkgCertificate := filepath.Join(appDir, "security", "clients.pem")
	homeDir := cli.config.homeDir
	certificate := filepath.Join(homeDir, app.String(), "data-plane-public-cert.pem")
	privateKey := filepath.Join(homeDir, app.String(), "data-plane-private-key.pem")

	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Private key written to %s\nSuccess: Copied certificate from %s to %s\n", certificate, privateKey, certificate, pkgCertificate), stdout.String())
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
	configureCloud(t, cli)
	stdout.Reset()
	stderr.Reset()

	args := append(subcommand, pkgDir)
	err = cli.Run(args...)
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), "Error: cannot add certificate to compressed application package")

	err = os.Remove(zipFile)
	assert.Nil(t, err)

	args = append(subcommand, "-f", pkgDir)
	err = cli.Run(args...)
	assert.Nil(t, err)
	assert.Contains(t, stdout.String(), "Success: Certificate written to")
	assert.Contains(t, stdout.String(), "Success: Private key written to")
}

func TestCertAdd(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	configureCloud(t, cli)
	stdout.Reset()
	require.Nil(t, cli.Run("auth", "cert", "-N"))

	appDir, pkgDir := mock.ApplicationPackageDir(t, false, false)
	stdout.Reset()
	require.Nil(t, cli.Run("auth", "cert", "add", pkgDir))
	pkgCertificate := filepath.Join(appDir, "security", "clients.pem")
	homeDir := cli.config.homeDir
	certificate := filepath.Join(homeDir, "t1.a1.i1", "data-plane-public-cert.pem")
	assert.Equal(t, fmt.Sprintf("Success: Copied certificate from %s to %s\n", certificate, pkgCertificate), stdout.String())

	require.NotNil(t, cli.Run("auth", "cert", "add", pkgDir))
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: application package %s already contains a certificate", appDir))
	stdout.Reset()
	require.Nil(t, cli.Run("auth", "cert", "add", "-f", pkgDir))
	assert.Equal(t, fmt.Sprintf("Success: Copied certificate from %s to %s\n", certificate, pkgCertificate), stdout.String())
}

func TestCertNoAdd(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	configureCloud(t, cli)
	stdout.Reset()
	require.Nil(t, cli.Run("auth", "cert", "-N"))
	homeDir := cli.config.homeDir

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)

	certificate := filepath.Join(homeDir, app.String(), "data-plane-public-cert.pem")
	privateKey := filepath.Join(homeDir, app.String(), "data-plane-private-key.pem")
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Private key written to %s\n", certificate, privateKey), stdout.String())

	require.NotNil(t, cli.Run("auth", "cert", "-N"))
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: private key %s already exists", privateKey))
	require.Nil(t, os.Remove(privateKey))

	stderr.Reset()
	require.NotNil(t, cli.Run("auth", "cert", "-N"))
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: certificate %s already exists", certificate))

	stdout.Reset()
	require.Nil(t, cli.Run("auth", "cert", "-N", "-f"))
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to %s\nSuccess: Private key written to %s\n", certificate, privateKey), stdout.String())
}
