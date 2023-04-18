// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
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

	// target
	assertConfigCommand(t, configHome, "target = local\n", "config", "get", "target") // default value
	assertConfigCommand(t, configHome, "", "config", "set", "target", "hosted")
	assertConfigCommand(t, configHome, "target = hosted\n", "config", "get", "target")
	assertConfigCommand(t, configHome, "", "config", "set", "target", "cloud")
	assertConfigCommand(t, configHome, "target = cloud\n", "config", "get", "target")
	assertConfigCommand(t, configHome, "", "config", "set", "target", "http://127.0.0.1:8080")
	assertConfigCommand(t, configHome, "", "config", "set", "target", "https://127.0.0.1")
	assertConfigCommand(t, configHome, "target = https://127.0.0.1\n", "config", "get", "target")
	assertConfigCommand(t, configHome, "target = local\n", "config", "get", "-t", "local", "target")

	// application
	assertConfigCommandErr(t, configHome, "Error: invalid application: \"foo\"\n", "config", "set", "application", "foo")
	assertConfigCommand(t, configHome, "application = <unset>\n", "config", "get", "application")
	assertConfigCommand(t, configHome, "", "config", "set", "application", "t1.a1.i1")
	assertConfigCommand(t, configHome, "application = t1.a1.i1\n", "config", "get", "application")
	assertConfigCommand(t, configHome, "", "config", "set", "application", "t1.a1")
	assertConfigCommand(t, configHome, "application = t1.a1.default\n", "config", "get", "application")

	// cluster
	assertConfigCommand(t, configHome, "cluster = <unset>\n", "config", "get", "cluster")
	assertConfigCommand(t, configHome, "", "config", "set", "cluster", "feed")
	assertConfigCommand(t, configHome, "cluster = feed\n", "config", "get", "cluster")

	// instance
	assertConfigCommand(t, configHome, "instance = <unset>\n", "config", "get", "instance")
	assertConfigCommand(t, configHome, "", "config", "set", "instance", "i2")
	assertConfigCommand(t, configHome, "instance = i2\n", "config", "get", "instance")

	// wait
	assertConfigCommandErr(t, configHome, "Error: wait option must be an integer >= 0, got \"foo\"\n", "config", "set", "wait", "foo")
	assertConfigCommand(t, configHome, "", "config", "set", "wait", "60")
	assertConfigCommand(t, configHome, "wait = 60\n", "config", "get", "wait")
	assertConfigCommand(t, configHome, "wait = 30\n", "config", "get", "--wait", "30", "wait") // flag overrides global config

	// color
	assertConfigCommandErr(t, configHome, "Error: invalid option or value: color = foo\n", "config", "set", "color", "foo")
	assertConfigCommand(t, configHome, "", "config", "set", "color", "never")
	assertConfigCommand(t, configHome, "color = never\n", "config", "get", "color")
	assertConfigCommand(t, configHome, "", "config", "unset", "color")
	assertConfigCommand(t, configHome, "color = auto\n", "config", "get", "color")

	// quiet
	assertConfigCommand(t, configHome, "", "config", "set", "quiet", "true")
	assertConfigCommand(t, configHome, "", "config", "set", "quiet", "false")

	// zone
	assertConfigCommand(t, configHome, "", "config", "set", "zone", "dev.us-east-1")
	assertConfigCommand(t, configHome, "zone = dev.us-east-1\n", "config", "get", "zone")

	// Write empty value to YAML config, which should be ignored. This is for compatibility with older config formats
	configFile := filepath.Join(configHome, "config.yaml")
	assertConfigCommand(t, configHome, "", "config", "unset", "zone")
	data, err := os.ReadFile(configFile)
	require.Nil(t, err)
	yamlConfig := string(data)
	assert.NotContains(t, yamlConfig, "zone:")
	config := yamlConfig + "zone: \"\"\n"
	require.Nil(t, os.WriteFile(configFile, []byte(config), 0600))
	assertConfigCommand(t, configHome, "zone = <unset>\n", "config", "get", "zone")
}

func TestLocalConfig(t *testing.T) {
	configHome := t.TempDir()
	// Write a few global options
	assertConfigCommand(t, configHome, "", "config", "set", "instance", "main")
	assertConfigCommand(t, configHome, "", "config", "set", "target", "cloud")

	// Change directory to an application package and write local options
	_, rootDir := mock.ApplicationPackageDir(t, false, false)
	wd, err := os.Getwd()
	require.Nil(t, err)
	t.Cleanup(func() { os.Chdir(wd) })
	require.Nil(t, os.Chdir(rootDir))
	assertConfigCommandStdErr(t, configHome, "Warning: no local configuration present\n", "config", "get", "--local")
	assertConfigCommand(t, configHome, "", "config", "set", "--local", "instance", "foo")
	assertConfigCommand(t, configHome, "instance = foo\n", "config", "get", "instance")
	assertConfigCommand(t, configHome, "instance = bar\n", "config", "get", "--instance", "bar", "instance") // flag overrides local config

	// get --local prints only options set in local config
	assertConfigCommand(t, configHome, "instance = foo\n", "config", "get", "--local")

	// get reads global option if unset locally
	assertConfigCommand(t, configHome, "target = cloud\n", "config", "get", "target")

	// get merges settings from local and global config
	assertConfigCommand(t, configHome, "", "config", "set", "--local", "application", "t1.a1")
	assertConfigCommand(t, configHome, `application = t1.a1.default
cluster = <unset>
color = auto
instance = foo
quiet = false
target = cloud
wait = 0
zone = <unset>
`, "config", "get")

	// Only locally set options are written
	localConfig, err := os.ReadFile(filepath.Join(rootDir, ".vespa", "config.yaml"))
	require.Nil(t, err)
	assert.Equal(t, "application: t1.a1.default\ninstance: foo\n", string(localConfig))

	// Changing back to original directory reads from global config
	require.Nil(t, os.Chdir(wd))
	assertConfigCommand(t, configHome, "instance = main\n", "config", "get", "instance")
	assertConfigCommand(t, configHome, "target = cloud\n", "config", "get", "target")
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
	key, err := cli.config.readAPIKey(cli, vespa.PublicSystem, "t1")
	assert.Nil(t, key)
	require.NotNil(t, err)

	// From default path when it exists
	require.Nil(t, os.WriteFile(filepath.Join(cli.config.homeDir, "t1.api-key.pem"), []byte("foo"), 0600))
	key, err = cli.config.readAPIKey(cli, vespa.PublicSystem, "t1")
	require.Nil(t, err)
	assert.Equal(t, []byte("foo"), key)

	// Cloud CI never reads key from disk as it's not expected to have any
	cli, _, _ = newTestCLI(t, "VESPA_CLI_CLOUD_CI=true")
	key, err = cli.config.readAPIKey(cli, vespa.PublicSystem, "t1")
	require.Nil(t, err)
	assert.Nil(t, key)

	// From file specified in environment
	keyFile := filepath.Join(t.TempDir(), "key")
	require.Nil(t, os.WriteFile(keyFile, []byte("bar"), 0600))
	cli, _, _ = newTestCLI(t, "VESPA_CLI_API_KEY_FILE="+keyFile)
	key, err = cli.config.readAPIKey(cli, vespa.PublicSystem, "t1")
	require.Nil(t, err)
	assert.Equal(t, []byte("bar"), key)

	// From key specified in environment
	cli, _, _ = newTestCLI(t, "VESPA_CLI_API_KEY=baz")
	key, err = cli.config.readAPIKey(cli, vespa.PublicSystem, "t1")
	require.Nil(t, err)
	assert.Equal(t, []byte("baz"), key)

	// Prefer Auth0 if we have auth config
	cli, _, _ = newTestCLI(t)
	require.Nil(t, os.WriteFile(filepath.Join(cli.config.homeDir, "auth.json"), []byte("foo"), 0600))
	key, err = cli.config.readAPIKey(cli, vespa.PublicSystem, "t1")
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
			TrustAll:      true,
			CACertificate: []byte("cacert"),
			KeyPair:       []tls.Certificate{keyPair},
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
	require.Nil(t, os.WriteFile(certFile, pemCert, 0600))
	require.Nil(t, os.WriteFile(keyFile, pemKey, 0600))
	require.Nil(t, os.WriteFile(caCertFile, []byte("cacert"), 0600))
	assertTLSOptions(t, homeDir, app,
		vespa.TargetLocal,
		vespa.TLSOptions{
			KeyPair:           []tls.Certificate{keyPair},
			CACertificate:     []byte("cacert"),
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
	require.Nil(t, os.WriteFile(defaultCertFile, pemCert, 0600))
	require.Nil(t, os.WriteFile(defaultKeyFile, pemKey, 0600))
	assertTLSOptions(t, homeDir, app,
		vespa.TargetLocal,
		vespa.TLSOptions{
			KeyPair:         []tls.Certificate{keyPair},
			CertificateFile: defaultCertFile,
			PrivateKeyFile:  defaultKeyFile,
		},
	)
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
