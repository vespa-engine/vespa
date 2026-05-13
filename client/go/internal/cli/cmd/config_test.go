// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func TestConfig(t *testing.T) {
	configHome := t.TempDir()
	assertConfigCommandErr(t, configHome, "Error: invalid option or value: foo = bar\n", "config", "set", "foo", "bar")
	assertConfigCommandErr(t, configHome, "Error: invalid option: foo\n", "config", "get", "foo")

	gh := globalConfigHeader(configHome)

	// target
	assertConfigCommand(t, configHome, gh+"target = local\n", "config", "get", "target") // default value
	assertConfigCommand(t, configHome, successSet(configHome, "target", "hosted"), "config", "set", "target", "hosted")
	assertConfigCommand(t, configHome, gh+"target = hosted\n", "config", "get", "target")
	assertConfigCommand(t, configHome, successSet(configHome, "target", "cloud"), "config", "set", "target", "cloud")
	assertConfigCommand(t, configHome, gh+"target = cloud\n", "config", "get", "target")
	assertConfigCommand(t, configHome, successSet(configHome, "target", "http://127.0.0.1:8080"), "config", "set", "target", "http://127.0.0.1:8080")
	assertConfigCommand(t, configHome, successSet(configHome, "target", "https://127.0.0.1"), "config", "set", "target", "https://127.0.0.1")
	assertConfigCommand(t, configHome, gh+"target = https://127.0.0.1\n", "config", "get", "target")
	assertConfigCommand(t, configHome, gh+"target = local\n", "config", "get", "-t", "local", "target")
	// Internal test system targets
	assertConfigCommand(t, configHome, successSet(configHome, "target", "cd"), "config", "set", "target", "cd")
	assertConfigCommand(t, configHome, gh+"target = cd\n", "config", "get", "target")
	assertConfigCommand(t, configHome, successSet(configHome, "target", "publiccd"), "config", "set", "target", "publiccd")
	assertConfigCommand(t, configHome, gh+"target = publiccd\n", "config", "get", "target")

	// application
	assertConfigCommandErr(t, configHome, "Error: invalid application: \"foo\"\n", "config", "set", "application", "foo")
	assertConfigCommand(t, configHome, gh+"application = <unset>\n", "config", "get", "application")
	assertConfigCommand(t, configHome, successSet(configHome, "application", "t1.a1.i1"), "config", "set", "application", "t1.a1.i1")
	assertConfigCommand(t, configHome, gh+"application = t1.a1.i1\n", "config", "get", "application")
	assertConfigCommand(t, configHome, successSet(configHome, "application", "t1.a1"), "config", "set", "application", "t1.a1")
	assertConfigCommand(t, configHome, gh+"application = t1.a1.default\n", "config", "get", "application")

	// cluster
	assertConfigCommand(t, configHome, gh+"cluster = <unset>\n", "config", "get", "cluster")
	assertConfigCommand(t, configHome, successSet(configHome, "cluster", "feed"), "config", "set", "cluster", "feed")
	assertConfigCommand(t, configHome, gh+"cluster = feed\n", "config", "get", "cluster")

	// instance
	assertConfigCommand(t, configHome, gh+"instance = <unset>\n", "config", "get", "instance")
	assertConfigCommand(t, configHome, successSet(configHome, "instance", "i2"), "config", "set", "instance", "i2")
	assertConfigCommand(t, configHome, gh+"instance = i2\n", "config", "get", "instance")

	// color
	assertConfigCommandErr(t, configHome, "Error: invalid option or value: color = foo\n", "config", "set", "color", "foo")
	assertConfigCommand(t, configHome, successSet(configHome, "color", "never"), "config", "set", "color", "never")
	assertConfigCommand(t, configHome, gh+"color = never\n", "config", "get", "color")
	assertConfigCommand(t, configHome, successUnset(configHome, "color"), "config", "unset", "color")
	assertConfigCommand(t, configHome, gh+"color = auto\n", "config", "get", "color")

	// quiet — setting quiet=true suppresses stdout for subsequent commands
	assertConfigCommand(t, configHome, successSet(configHome, "quiet", "true"), "config", "set", "quiet", "true")
	assertConfigCommand(t, configHome, "", "config", "set", "quiet", "false") // quiet=true loaded, stdout discarded

	// zone
	assertConfigCommand(t, configHome, successSet(configHome, "zone", "dev.us-east-1"), "config", "set", "zone", "dev.us-east-1")
	assertConfigCommand(t, configHome, gh+"zone = dev.us-east-1\n", "config", "get", "zone")
	assertConfigCommand(t, configHome, gh+"zone = prod.us-north-1\n", "config", "get", "--zone", "prod.us-north-1", "zone") // flag overrides global config

	// Write empty value to YAML config, which should be ignored. This is for compatibility with older config formats
	configFile := filepath.Join(configHome, "config.yaml")
	assertConfigCommand(t, configHome, successUnset(configHome, "zone"), "config", "unset", "zone")
	data, err := os.ReadFile(configFile)
	require.Nil(t, err)
	yamlConfig := string(data)
	assert.NotContains(t, yamlConfig, "zone:")
	config := yamlConfig + "zone: \"\"\n"
	require.Nil(t, os.WriteFile(configFile, []byte(config), 0o600))
	assertConfigCommand(t, configHome, gh+"zone = <unset>\n", "config", "get", "zone")
}

func TestLocalConfig(t *testing.T) {
	configHome := t.TempDir()
	// Write a few global options
	assertConfigCommand(t, configHome, successSet(configHome, "instance", "main"), "config", "set", "instance", "main")
	assertConfigCommand(t, configHome, successSet(configHome, "target", "cloud"), "config", "set", "target", "cloud")

	// Change directory to an application package and write local options
	_, rootDir := mock.ApplicationPackageDir(t, false, false)
	wd, err := os.Getwd()
	require.Nil(t, err)
	t.Cleanup(func() { os.Chdir(wd) })
	require.Nil(t, os.Chdir(rootDir))
	gh := globalConfigHeader(configHome)
	lh := localConfigHeader(rootDir)

	assertConfigCommandStdErr(t, configHome, "Warning: no local configuration present\n", "config", "get", "--local")
	assertConfigCommand(t, configHome, successSetLocal(rootDir, "instance", "foo"), "config", "set", "--local", "instance", "foo")
	assertConfigCommand(t, configHome, gh+"instance = foo\n", "config", "get", "instance")
	assertConfigCommand(t, configHome, gh+"instance = bar\n", "config", "get", "--instance", "bar", "instance") // flag overrides local config

	// get --local prints only options set in local config
	assertConfigCommand(t, configHome, lh+"instance = foo\n", "config", "get", "--local")

	// get reads global option if unset locally
	assertConfigCommand(t, configHome, gh+"target = cloud\n", "config", "get", "target")

	// get merges settings from local and global config
	assertConfigCommand(t, configHome, successSetLocal(rootDir, "application", "t1.a1"), "config", "set", "--local", "application", "t1.a1")
	assertConfigCommand(t, configHome, gh+`application = t1.a1.default
cluster = <unset>
color = auto
debug = false
default_config_scope = <unset>
instance = foo
quiet = false
target = cloud
zone = <unset>
`, "config", "get")

	// Only locally set options are written
	localConfig, err := os.ReadFile(filepath.Join(rootDir, ".vespa", "config.yaml"))
	require.Nil(t, err)
	assert.Equal(t, "application: t1.a1.default\ninstance: foo\n", string(localConfig))

	// get prints local config when in a sub-directory of the application package
	subDir := filepath.Join(rootDir, "a", "b")
	require.Nil(t, os.MkdirAll(subDir, 0755))
	require.Nil(t, os.Chdir(subDir))
	assertConfigCommand(t, configHome, lh+"instance = foo\n", "config", "get", "--local", "instance")

	// Changing back to original directory reads from global config
	require.Nil(t, os.Chdir(wd))
	assertConfigCommand(t, configHome, gh+"instance = main\n", "config", "get", "instance")
	assertConfigCommand(t, configHome, gh+"target = cloud\n", "config", "get", "target")
}

func TestLocalConfigSearch(t *testing.T) {
	// Create a directory structure: tmpDir/app/.vespa/config.yaml and tmpDir/app/src/main
	tmpDir := t.TempDir()
	appDir := filepath.Join(tmpDir, "app")
	vespaDir := filepath.Join(appDir, ".vespa")
	srcDir := filepath.Join(appDir, "src", "main")
	require.Nil(t, os.MkdirAll(vespaDir, 0755))
	require.Nil(t, os.MkdirAll(srcDir, 0755))

	// Write local config
	configFile := filepath.Join(vespaDir, "config.yaml")
	require.Nil(t, os.WriteFile(configFile, []byte("instance: from-parent\n"), 0644))

	// Change to deep subdirectory
	wd, err := os.Getwd()
	require.Nil(t, err)
	t.Cleanup(func() { os.Chdir(wd) })
	require.Nil(t, os.Chdir(srcDir))

	// Config should be found by walking up
	configHome := t.TempDir()
	assertConfigCommand(t, configHome, localConfigHeader(appDir)+"instance = from-parent\n", "config", "get", "--local", "instance")

	// Config in sibling directory should not be found
	siblingDir := filepath.Join(tmpDir, "other")
	require.Nil(t, os.MkdirAll(siblingDir, 0755))
	require.Nil(t, os.Chdir(siblingDir))
	assertConfigCommandStdErr(t, configHome, "Warning: no local configuration present\n", "config", "get", "--local")
}

func resolveSymlinks(path string) string {
	if real, err := filepath.EvalSymlinks(path); err == nil {
		return real
	}
	return path
}

func globalConfigHeader(configHome string) string {
	return "Global config at " + filepath.Join(configHome, "config.yaml") + "\n"
}

func localConfigHeader(localDir string) string {
	return "Local config at " + filepath.Join(resolveSymlinks(localDir), ".vespa", "config.yaml") + "\n"
}

func successSet(configHome, option, value string) string {
	return fmt.Sprintf("Success: set %s to %s in global config at %s\n", option, value, filepath.Join(configHome, "config.yaml"))
}

func successSetLocal(localDir, option, value string) string {
	return fmt.Sprintf("Success: set %s to %s in local config at %s\n", option, value, filepath.Join(resolveSymlinks(localDir), ".vespa", "config.yaml"))
}

func successUnset(configHome, option string) string {
	return fmt.Sprintf("Success: unset %s in global config at %s\n", option, filepath.Join(configHome, "config.yaml"))
}

func TestDefaultConfigScopeWarning(t *testing.T) {
	warnLine := "Warning: default_config_scope is unset, wrote to global config\n"
	hintLine := "Hint: set default_config_scope to \"local\" or \"global\" to silence this warning; in Vespa 9 unset will default to \"local\"\n"
	expectedStderr := warnLine + hintLine

	// Warning fires when default_config_scope is unset and no explicit scope flag.
	// Each CLI gets its own empty home dir (overrides the default written by newTestCLI).
	cli, _, stderr := newTestCLI(t, "VESPA_CLI_HOME="+t.TempDir())
	require.Nil(t, cli.Run("config", "set", "target", "cloud"))
	assert.Equal(t, expectedStderr, stderr.String())

	// --local and --global suppress the warning (explicit scope provided).
	_, rootDir := mock.ApplicationPackageDir(t, false, false)
	wd, err := os.Getwd()
	require.Nil(t, err)
	t.Cleanup(func() { os.Chdir(wd) })
	require.Nil(t, os.Chdir(rootDir))

	cli2, _, stderr2 := newTestCLI(t, "VESPA_CLI_HOME="+t.TempDir())
	require.Nil(t, cli2.Run("config", "set", "--local", "target", "cloud"))
	assert.Equal(t, "", stderr2.String())

	cli3, _, stderr3 := newTestCLI(t, "VESPA_CLI_HOME="+t.TempDir())
	require.Nil(t, cli3.Run("config", "set", "--global", "target", "cloud"))
	assert.Equal(t, "", stderr3.String())
}

func TestDefaultConfigScope(t *testing.T) {
	configHome := t.TempDir()

	// Invalid value rejected with hints
	assertConfigCommandErr(t, configHome,
		"Error: invalid value for default_config_scope: \"bad\"\nHint: valid values are \"local\" and \"global\"\nHint: when unset, defaults to \"global\"; in Vespa 9 the default will change to \"local\"\n",
		"config", "set", "default_config_scope", "bad")

	gh := globalConfigHeader(configHome)

	// Set and get
	assertConfigCommand(t, configHome, successSet(configHome, "default_config_scope", "local"), "config", "set", "default_config_scope", "local")
	assertConfigCommand(t, configHome, gh+"default_config_scope = local\n", "config", "get", "default_config_scope")
	assertConfigCommand(t, configHome, successSet(configHome, "default_config_scope", "global"), "config", "set", "default_config_scope", "global")
	assertConfigCommand(t, configHome, gh+"default_config_scope = global\n", "config", "get", "default_config_scope")

	// Change to an application package dir
	_, rootDir := mock.ApplicationPackageDir(t, false, false)
	wd, err := os.Getwd()
	require.Nil(t, err)
	t.Cleanup(func() { os.Chdir(wd) })
	require.Nil(t, os.Chdir(rootDir))

	// --local flag with default_config_scope is an error
	assertConfigCommandErr(t, configHome, "Error: default_config_scope can only be modified in global configuration\n", "config", "set", "--local", "default_config_scope", "local")

	// --local and --global together is an error
	assertConfigCommandErr(t, configHome, "Error: cannot use both --local and --global flags\n", "config", "set", "--local", "--global", "target", "cloud")
	assertConfigCommandErr(t, configHome, "Error: cannot use both --local and --global flags\n", "config", "unset", "--local", "--global", "target")

	// With default_config_scope=local, config set writes to local config without --local flag
	assertConfigCommand(t, configHome, successSet(configHome, "default_config_scope", "local"), "config", "set", "default_config_scope", "local")
	assertConfigCommand(t, configHome, successSetLocal(rootDir, "target", "cloud"), "config", "set", "target", "cloud")

	// --global forces global config even when default_config_scope=local
	assertConfigCommand(t, configHome, successSet(configHome, "target", "cloud"), "config", "set", "--global", "target", "cloud")
	assertConfigCommand(t, configHome, successUnset(configHome, "target"), "config", "unset", "--global", "target")

	// Explicit --local still works and overrides default_config_scope
	assertConfigCommand(t, configHome, successSetLocal(rootDir, "instance", "foo"), "config", "set", "--local", "instance", "foo")

	// config get defaults to local when default_config_scope=local and inside an app package
	lh := localConfigHeader(rootDir)
	assertConfigCommand(t, configHome, lh+"instance = foo\ntarget = cloud\n", "config", "get")

	// config get --global shows global config even when default_config_scope=local
	gh2 := globalConfigHeader(configHome)
	assertConfigCommand(t, configHome, lh+"default_config_scope = <unset>\n", "config", "get", "default_config_scope")
	assertConfigCommand(t, configHome, gh2+"default_config_scope = local\n", "config", "get", "--global", "default_config_scope")

	// config get --local and --global together is an error
	assertConfigCommandErr(t, configHome, "Error: cannot use both --local and --global flags\n", "config", "get", "--local", "--global")
}

func assertConfigCommand(t *testing.T, configHome, expected string, args ...string) {
	t.Helper()
	assertEnvConfigCommand(t, configHome, expected, nil, args...)
}

func assertEnvConfigCommand(t *testing.T, configHome, expected string, env []string, args ...string) {
	t.Helper()
	env = append(env, "VESPA_CLI_HOME="+configHome)
	cli, stdout, _ := newTestCLI(t, env...)
	err := cli.Run(args...)
	assert.Nil(t, err)
	assert.Equal(t, expected, stdout.String())
}

func assertConfigCommandStdErr(t *testing.T, configHome, expected string, args ...string) error {
	t.Helper()
	cli, _, stderr := newTestCLI(t)
	err := cli.Run(args...)
	assert.Equal(t, expected, stderr.String())
	return err
}

func assertConfigCommandErr(t *testing.T, configHome, expected string, args ...string) {
	t.Helper()
	assert.NotNil(t, assertConfigCommandStdErr(t, configHome, expected, args...))
}

func TestReadAPIKey(t *testing.T) {
	cli, _, _ := newTestCLI(t)
	key, err := cli.config.readAPIKey(cli, "t1")
	assert.Nil(t, key)
	require.NotNil(t, err)

	// From default path when it exists
	require.Nil(t, os.WriteFile(filepath.Join(cli.config.homeDir, "t1.api-key.pem"), []byte("foo"), 0o600))
	key, err = cli.config.readAPIKey(cli, "t1")
	require.Nil(t, err)
	assert.Equal(t, []byte("foo"), key)

	// Cloud CI never reads key from disk as it's not expected to have any
	cli, _, _ = newTestCLI(t, "VESPA_CLI_CLOUD_CI=true")
	key, err = cli.config.readAPIKey(cli, "t1")
	require.Nil(t, err)
	assert.Nil(t, key)

	// From file specified in environment
	keyFile := filepath.Join(t.TempDir(), "key")
	require.Nil(t, os.WriteFile(keyFile, []byte("bar"), 0o600))
	cli, _, _ = newTestCLI(t, "VESPA_CLI_API_KEY_FILE="+keyFile)
	key, err = cli.config.readAPIKey(cli, "t1")
	require.Nil(t, err)
	assert.Equal(t, []byte("bar"), key)

	// From key specified in environment
	cli, _, _ = newTestCLI(t, "VESPA_CLI_API_KEY=baz")
	key, err = cli.config.readAPIKey(cli, "t1")
	require.Nil(t, err)
	assert.Equal(t, []byte("baz"), key)

	// Prefer Auth0 if we have auth config
	cli, _, _ = newTestCLI(t)
	require.Nil(t, os.WriteFile(filepath.Join(cli.config.homeDir, "auth.json"), []byte("foo"), 0o600))
	key, err = cli.config.readAPIKey(cli, "t1")
	require.Nil(t, err)
	assert.Nil(t, key)
}

func TestConfigReadTLSOptions(t *testing.T) {
	app := vespa.ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"}
	homeDir := t.TempDir()

	// No environment variables, and no files on disk
	assertTLSOptions(t, homeDir, app, vespa.TargetLocal, vespa.TLSOptions{})

	// A single environment variable is set
	assertTLSOptions(t, homeDir, app, vespa.TargetLocal, vespa.TLSOptions{TrustAll: true}, "VESPA_CLI_DATA_PLANE_TRUST_ALL=true")

	// Key pair is provided in-line in environment variables
	pemCert, pemKey, keyPair := createKeyPair(t)
	assertTLSOptions(t, homeDir, app,
		vespa.TargetLocal,
		vespa.TLSOptions{
			TrustAll:          true,
			KeyPair:           []tls.Certificate{keyPair},
			CACertificatePEM:  []byte("cacert"),
			CertificatePEM:    pemCert,
			PrivateKeyPEM:     pemKey,
			CACertificateFile: "VESPA_CLI_DATA_PLANE_CA_CERT",
			CertificateFile:   "VESPA_CLI_DATA_PLANE_CERT",
			PrivateKeyFile:    "VESPA_CLI_DATA_PLANE_KEY",
		},
		"VESPA_CLI_DATA_PLANE_TRUST_ALL=true",
		"VESPA_CLI_DATA_PLANE_CA_CERT=cacert",
		"VESPA_CLI_DATA_PLANE_CERT="+string(pemCert),
		"VESPA_CLI_DATA_PLANE_KEY="+string(pemKey),
	)

	// Key pair is provided as file paths through environment variables
	certFile := filepath.Join(homeDir, "cert")
	keyFile := filepath.Join(homeDir, "key")
	caCertFile := filepath.Join(homeDir, "cacert")
	require.Nil(t, os.WriteFile(certFile, pemCert, 0o600))
	require.Nil(t, os.WriteFile(keyFile, pemKey, 0o600))
	require.Nil(t, os.WriteFile(caCertFile, []byte("cacert"), 0o600))
	assertTLSOptions(t, homeDir, app,
		vespa.TargetLocal,
		vespa.TLSOptions{
			KeyPair:           []tls.Certificate{keyPair},
			CACertificatePEM:  []byte("cacert"),
			CertificatePEM:    pemCert,
			PrivateKeyPEM:     pemKey,
			CACertificateFile: caCertFile,
			CertificateFile:   certFile,
			PrivateKeyFile:    keyFile,
		},
		"VESPA_CLI_DATA_PLANE_CERT_FILE="+certFile,
		"VESPA_CLI_DATA_PLANE_KEY_FILE="+keyFile,
		"VESPA_CLI_DATA_PLANE_CA_CERT_FILE="+caCertFile,
	)

	// Key pair resides in default paths
	defaultCertFile := filepath.Join(homeDir, app.String(), "data-plane-public-cert.pem")
	defaultKeyFile := filepath.Join(homeDir, app.String(), "data-plane-private-key.pem")
	require.Nil(t, os.WriteFile(defaultCertFile, pemCert, 0o600))
	require.Nil(t, os.WriteFile(defaultKeyFile, pemKey, 0o600))
	assertTLSOptions(t, homeDir, app,
		vespa.TargetLocal,
		vespa.TLSOptions{
			KeyPair:         []tls.Certificate{keyPair},
			CertificatePEM:  pemCert,
			PrivateKeyPEM:   pemKey,
			CertificateFile: defaultCertFile,
			PrivateKeyFile:  defaultKeyFile,
		},
	)

	// Key pair files specified through environment are required
	nonExistentFile := filepath.Join(homeDir, "non-existent-file")
	cli, _, _ := newTestCLI(t, "VESPA_CLI_DATA_PLANE_CERT_FILE="+nonExistentFile, "VESPA_CLI_DATA_PLANE_KEY_FILE="+nonExistentFile)
	_, err := cli.config.readTLSOptions(app, vespa.TargetLocal)
	assert.True(t, os.IsNotExist(err))
}

func TestConfigTargetResolving(t *testing.T) {
	cli, _, _ := newTestCLI(t)
	require.Nil(t, cli.Run("config", "set", "target", "https://example.com"))
	assertTargetType(t, vespa.TargetCustom, cli)
	require.Nil(t, cli.Run("config", "set", "target", "https://foo.bar.vespa-team.no-north-1.dev.z.vespa-app.cloud"))
	assertTargetType(t, vespa.TargetCloud, cli)
	require.Nil(t, cli.Run("config", "set", "target", "https://foo.bar.vespa-team.no-north-1.dev.z.vespa.oath.cloud:4443"))
	assertTargetType(t, vespa.TargetHosted, cli)
}

func assertTargetType(t *testing.T, expected string, cli *CLI) {
	targetType, err := cli.targetType(anyTarget)
	require.Nil(t, err)
	assert.Equal(t, expected, targetType.name)
}

func assertTLSOptions(t *testing.T, homeDir string, app vespa.ApplicationID, target string, want vespa.TLSOptions, envVars ...string) {
	t.Helper()
	envVars = append(envVars, "VESPA_CLI_HOME="+homeDir)
	cli, _, _ := newTestCLI(t, envVars...)
	require.Nil(t, cli.Run("config", "set", "application", app.String()))
	config, err := cli.config.readTLSOptions(app, vespa.TargetLocal)
	require.Nil(t, err)
	assert.Equal(t, want, config)
}

func createKeyPair(t *testing.T) ([]byte, []byte, tls.Certificate) {
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	notBefore := time.Now()
	notAfter := notBefore.Add(24 * time.Hour)
	template := x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "example.com"},
		NotBefore:    notBefore,
		NotAfter:     notAfter,
	}
	certificateDER, err := x509.CreateCertificate(rand.Reader, &template, &template, &privateKey.PublicKey, privateKey)
	if err != nil {
		t.Fatal(err)
	}
	privateKeyDER, err := x509.MarshalPKCS8PrivateKey(privateKey)
	if err != nil {
		t.Fatal(err)
	}
	pemCert := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: certificateDER})
	pemKey := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: privateKeyDER})
	kp, err := tls.X509KeyPair(pemCert, pemKey)
	if err != nil {
		t.Fatal(err)
	}
	return pemCert, pemKey, kp
}
