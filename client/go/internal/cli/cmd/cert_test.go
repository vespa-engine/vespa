// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: mpolden

package cmd

import (
	"bytes"
	"crypto/x509"
	"encoding/pem"
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

	assert.Equal(t, fmt.Sprintf("Success: Certificate written to '%s'\nSuccess: Private key written to '%s'\nSuccess: Copied certificate from '%s' to '%s'\n", certificate, privateKey, certificate, pkgCertificate), stdout.String())
}

func TestCertCompressedPackage(t *testing.T) {
	_, pkgDir := mock.ApplicationPackageDir(t, true, false)
	zipFile := filepath.Join(pkgDir, "target", "application.zip")
	err := os.MkdirAll(filepath.Dir(zipFile), 0o755)
	assert.Nil(t, err)
	_, err = os.Create(zipFile)
	assert.Nil(t, err)

	cli, stdout, stderr := newTestCLI(t)
	configureCloud(t, cli)
	stdout.Reset()
	stderr.Reset()

	err = cli.Run("auth", "cert", zipFile)
	assert.NotNil(t, err)
	assert.Contains(t, stderr.String(), "Error: cannot add certificate to compressed application package")

	err = os.Remove(zipFile)
	assert.Nil(t, err)

	err = cli.Run("auth", "cert", "-f", pkgDir)
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
	assert.Equal(t, fmt.Sprintf("Success: Copied certificate from '%s' to '%s'\n", certificate, pkgCertificate), stdout.String())

	require.NotNil(t, cli.Run("auth", "cert", "add", pkgDir))
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: application package '%s' already contains a certificate", appDir))
	stdout.Reset()
	require.Nil(t, cli.Run("auth", "cert", "add", "-f", pkgDir))
	assert.Equal(t, fmt.Sprintf("Success: Copied certificate from '%s' to '%s'\n", certificate, pkgCertificate), stdout.String())
}

func countPEMBlocks(t *testing.T, path string) int {
	t.Helper()
	data, err := os.ReadFile(path)
	require.Nil(t, err)
	count := 0
	for {
		var block *pem.Block
		block, data = pem.Decode(data)
		if block == nil {
			break
		}
		_, err := x509.ParseCertificate(block.Bytes)
		require.Nil(t, err)
		count++
	}
	return count
}

// setupRotation creates an initial certificate and performs one key rotation.
// Returns the homeDir and path to the certificate file.
func setupRotation(t *testing.T) (homeDir string, certFile string) {
	t.Helper()
	cli, _, _ := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("y\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--new-key"))
	app, err := vespa.ApplicationFromString("t1.a1.i1")
	require.Nil(t, err)
	return cli.config.homeDir, filepath.Join(cli.config.homeDir, app.String(), "data-plane-public-cert.pem")
}

func TestCertNewKey(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	require.Nil(t, err)
	certFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-public-cert.pem")
	backupKeyFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-private-key.pem.old")
	assert.Equal(t, 1, countPEMBlocks(t, certFile))

	stdout.Reset()
	stderr.Reset()
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("y\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--new-key"))

	assert.Contains(t, stdout.String(), "Certificate written to")
	assert.Contains(t, stderr.String(), "Private key backup created at")
	assert.Equal(t, 2, countPEMBlocks(t, certFile))
	_, err = os.Stat(backupKeyFile)
	assert.Nil(t, err, "backup key file should exist after rotation")
}

func TestCertNewKeyDecline(t *testing.T) {
	cli, _, _ := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	require.Nil(t, err)
	certFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-public-cert.pem")
	backupKeyFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-private-key.pem.old")

	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("n\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--new-key"))

	assert.Equal(t, 1, countPEMBlocks(t, certFile))
	_, err = os.Stat(backupKeyFile)
	assert.True(t, os.IsNotExist(err), "backup key file should not exist after declining rotation")
}

func TestCertNewKeyForce(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	require.Nil(t, err)
	certFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-public-cert.pem")
	backupKeyFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-private-key.pem.old")

	stdout.Reset()
	stderr.Reset()
	require.Nil(t, cli.Run("auth", "cert", "-N", "--new-key", "-f"))

	assert.Contains(t, stdout.String(), "Certificate written to")
	assert.Contains(t, stderr.String(), "Private key backup created at")
	assert.Equal(t, 2, countPEMBlocks(t, certFile))
	_, err = os.Stat(backupKeyFile)
	assert.Nil(t, err, "backup key file should exist after forced rotation")
}

func TestCertNewKeyBackupExists(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	configureCloud(t, cli)
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("y\n")
	require.Nil(t, cli.Run("auth", "cert", "-N"))
	require.Nil(t, cli.Run("auth", "cert", "-N", "--new-key"))

	// Subsequent rotations blocked because backup key still exists.
	cli.Stdin = bytes.NewBufferString("y\n")
	err := cli.Run("auth", "cert", "-N", "--new-key")
	require.NotNil(t, err)
	assert.Contains(t, stderr.String(), "backup of private key already exists")

	err = cli.Run("auth", "cert", "-N", "--new-key", "-f")
	require.NotNil(t, err)
	assert.Contains(t, stderr.String(), "backup of private key already exists")
}

func TestPruneOldNoExisting(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	configureCloud(t, cli)

	err := cli.Run("auth", "cert", "-N", "--prune-old")
	require.NotNil(t, err)
	assert.Contains(t, stderr.String(), "no certificate found")
	assert.Contains(t, stderr.String(), "Run 'vespa auth cert' first")
}

func TestPruneOldSingleCert(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))

	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old"))
	assert.Contains(t, stderr.String(), "Only one certificate is present")
}

func TestPruneOldNoCertMatchingCurrentKey(t *testing.T) {
	cli, _, stderr := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	require.Nil(t, err)
	certFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-public-cert.pem")
	keyFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-private-key.pem")

	// Duplicate cert to satisfy the len > 1 check.
	certData, err := os.ReadFile(certFile)
	require.Nil(t, err)
	require.Nil(t, os.WriteFile(certFile, append(certData, certData...), 0o600))

	// Replace private key with an unrelated key so no cert matches.
	unrelatedPair, err := vespa.CreateKeyPair()
	require.Nil(t, err)
	require.Nil(t, os.WriteFile(keyFile, unrelatedPair.PrivateKey, 0o600))

	err = cli.Run("auth", "cert", "-N", "--prune-old")
	require.NotNil(t, err)
	assert.Contains(t, stderr.String(), "no certificate in")
	assert.Contains(t, stderr.String(), "matches the current private key")
}

func TestPruneOldConfirm(t *testing.T) {
	homeDir, certFile := setupRotation(t)
	assert.Equal(t, 2, countPEMBlocks(t, certFile))

	cli, stdout, _ := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("y\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old"))

	assert.Contains(t, stdout.String(), "Pruned certificate file")
	assert.Equal(t, 1, countPEMBlocks(t, certFile))
}

func TestPruneOldDecline(t *testing.T) {
	homeDir, certFile := setupRotation(t)
	assert.Equal(t, 2, countPEMBlocks(t, certFile))

	cli, _, _ := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("n\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old"))

	assert.Equal(t, 2, countPEMBlocks(t, certFile))
}

func TestPruneOldForce(t *testing.T) {
	homeDir, certFile := setupRotation(t)
	assert.Equal(t, 2, countPEMBlocks(t, certFile))

	cli, stdout, _ := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old", "-f"))

	assert.Contains(t, stdout.String(), "Pruned certificate file")
	assert.Equal(t, 1, countPEMBlocks(t, certFile))
}

func TestPruneOldPartialRemoval(t *testing.T) {
	homeDir, certFile := setupRotation(t)

	// Append a third cert from an unknown key pair.
	unknownPair, err := vespa.CreateKeyPair()
	require.Nil(t, err)
	certData, err := os.ReadFile(certFile)
	require.Nil(t, err)
	require.Nil(t, os.WriteFile(certFile, append(certData, unknownPair.Certificate...), 0o600))
	assert.Equal(t, 3, countPEMBlocks(t, certFile))

	// Confirm removal of old cert (y), decline removal of unknown cert (n).
	cli2, stdout2, _ := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	cli2.isTerminal = func() bool { return true }
	cli2.Stdin = bytes.NewBufferString("y\nn\n")
	require.Nil(t, cli2.Run("auth", "cert", "-N", "--prune-old"))

	assert.Contains(t, stdout2.String(), "Pruned certificate file")
	assert.Equal(t, 2, countPEMBlocks(t, certFile))
}

func multipleCurrentCertsSetup(t *testing.T) (homeDir, certFile string) {
	t.Helper()
	cli, _, _ := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))
	app, err := vespa.ApplicationFromString("t1.a1.i1")
	require.Nil(t, err)
	certFile = filepath.Join(cli.config.homeDir, app.String(), "data-plane-public-cert.pem")
	certData, err := os.ReadFile(certFile)
	require.Nil(t, err)
	require.Nil(t, os.WriteFile(certFile, append(certData, certData...), 0o600))
	assert.Equal(t, 2, countPEMBlocks(t, certFile))
	return cli.config.homeDir, certFile
}

func TestPruneOldMultipleCurrentCertsDecline(t *testing.T) {
	homeDir, certFile := multipleCurrentCertsSetup(t)

	cli, _, stderr := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("n\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old"))

	assert.Contains(t, stderr.String(), "2 certificates match the current private key")
	assert.Equal(t, 2, countPEMBlocks(t, certFile))
}

func TestPruneOldMultipleCurrentCertsConfirm(t *testing.T) {
	homeDir, certFile := multipleCurrentCertsSetup(t)

	cli, stdout, stderr := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("y\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old"))

	assert.Contains(t, stderr.String(), "2 certificates match the current private key")
	assert.Contains(t, stdout.String(), "Pruned certificate file")
	assert.Equal(t, 1, countPEMBlocks(t, certFile))
}

func TestPruneOldMultipleCurrentCertsForce(t *testing.T) {
	homeDir, certFile := multipleCurrentCertsSetup(t)

	cli, stdout, _ := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old", "-f"))

	assert.Contains(t, stdout.String(), "Pruned certificate file")
	assert.Equal(t, 1, countPEMBlocks(t, certFile))
}

func TestPruneOldUnknownCert(t *testing.T) {
	cli, stdout, stderr := newTestCLI(t)
	configureCloud(t, cli)
	require.Nil(t, cli.Run("auth", "cert", "-N"))

	app, err := vespa.ApplicationFromString("t1.a1.i1")
	require.Nil(t, err)
	certFile := filepath.Join(cli.config.homeDir, app.String(), "data-plane-public-cert.pem")

	// Append a cert from an unknown key pair.
	unknownPair, err := vespa.CreateKeyPair()
	require.Nil(t, err)
	certData, err := os.ReadFile(certFile)
	require.Nil(t, err)
	require.Nil(t, os.WriteFile(certFile, append(certData, unknownPair.Certificate...), 0o600))
	assert.Equal(t, 2, countPEMBlocks(t, certFile))

	cli.isTerminal = func() bool { return true }
	cli.Stdin = bytes.NewBufferString("y\n")
	require.Nil(t, cli.Run("auth", "cert", "-N", "--prune-old"))

	assert.Contains(t, stderr.String(), "not associated with any of your saved private keys")
	assert.Contains(t, stdout.String(), "Pruned certificate file")
	assert.Equal(t, 1, countPEMBlocks(t, certFile))
}

func TestPruneOldUpdatesAppPackage(t *testing.T) {
	homeDir, certFile := setupRotation(t)
	assert.Equal(t, 2, countPEMBlocks(t, certFile))

	appDir, pkgDir := mock.ApplicationPackageDir(t, false, false)
	cli2, stdout2, _ := newTestCLI(t, "VESPA_CLI_HOME="+homeDir)
	cli2.isTerminal = func() bool { return true }
	cli2.Stdin = bytes.NewBufferString("y\n")
	require.Nil(t, cli2.Run("auth", "cert", "--prune-old", pkgDir))

	assert.Contains(t, stdout2.String(), "Pruned certificate file")
	assert.Contains(t, stdout2.String(), "Copied certificate")
	assert.Equal(t, 1, countPEMBlocks(t, certFile))
	assert.Equal(t, 1, countPEMBlocks(t, filepath.Join(appDir, "security", "clients.pem")))
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
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to '%s'\nSuccess: Private key written to '%s'\n", certificate, privateKey), stdout.String())

	require.NotNil(t, cli.Run("auth", "cert", "-N"))
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: private key '%s' already exists", privateKey))
	require.Nil(t, os.Remove(privateKey))

	stderr.Reset()
	require.NotNil(t, cli.Run("auth", "cert", "-N"))
	assert.Contains(t, stderr.String(), fmt.Sprintf("Error: certificate '%s' already exists", certificate))

	stdout.Reset()
	require.Nil(t, cli.Run("auth", "cert", "-N", "-f"))
	assert.Equal(t, fmt.Sprintf("Success: Certificate written to '%s'\nSuccess: Private key written to '%s'\n", certificate, privateKey), stdout.String())
}
