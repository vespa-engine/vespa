// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa config command
// author: bratseth

package cmd

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
	"github.com/vespa-engine/vespa/client/go/auth/auth0"
	"github.com/vespa-engine/vespa/client/go/config"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

const (
	configFile = "config.yaml"
)

func newConfigCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "config",
		Short: "Configure persistent values for global flags",
		Long: `Configure persistent values for global flags.

This command allows setting persistent values for global flags. On future
invocations the flag can then be omitted as it is read from the config file
instead.

Configuration is written to $HOME/.vespa by default. This path can be
overridden by setting the VESPA_CLI_HOME environment variable.

When setting an option locally, the configuration is written to .vespa in the
working directory, where that directory is assumed to be a Vespa application
directory. This allows you have separate configuration options per application.

Vespa CLI chooses the value for a given option in the following order, from
most to least preferred:

1. Flag value specified on the command line
2. Local config value
3. Global config value
4. Default value

The following flags/options can be configured:

application

Specifies the application ID to manage. It has three parts, separated by
dots, with the third part being optional. This is only relevant for the "cloud"
and "hosted" targets. See https://cloud.vespa.ai/en/tenant-apps-instances for
more details. This has no default value. Examples: tenant1.app1,
tenant1.app1.instance1

cluster

Specifies the container cluster to manage. If left empty (default) and the
application has only one container cluster, that cluster is chosen
automatically. When an application has multiple cluster this must be set a
valid cluster name, as specified in services.xml. See
https://docs.vespa.ai/en/reference/services-container.html for more details.

color

Controls how Vespa CLI uses colors. Setting this to "auto" (default) enables
colors if supported by the terminal, "never" completely disables colors and
"always" enables colors unilaterally.

instance

Specifies the instance of the application to manage. When specified, this takes
precedence over the instance specified as part of application. This has no
default value. Example: instance2

quiet

Print only errors.

target

Specifies the target to use for commands that interact with a Vespa platform,
e.g. vespa deploy or vespa query. Possible values are:

- local: (default) Connect to a Vespa platform running at localhost
- cloud: Connect to Vespa Cloud
- hosted: Connect to hosted Vespa (internal platform)
- *url*: Connect to a platform running at given URL.

wait

Specifies the number of seconds to wait for a service to become ready or
deployment to complete. Use this to have a potentially long-running command
block until the operation is complete, e.g. with vespa deploy. Defaults to 0
(no waiting)

zone

Specifies a custom dev or perf zone to use when connecting to a Vespa platform.
This is only relevant for cloud and hosted targets. By default, a zone is
chosen automatically. See https://cloud.vespa.ai/en/reference/zones for
available zones. Examples: dev.aws-us-east-1c, perf.aws-us-east-1c
`,
		DisableAutoGenTag: true,
		SilenceUsage:      false,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}
}

func newConfigSetCmd(cli *CLI) *cobra.Command {
	var localArg bool
	cmd := &cobra.Command{
		Use:   "set option-name value",
		Short: "Set a configuration option.",
		Example: `# Set the target to Vespa Cloud
$ vespa config set target cloud

# Set application, without a specific instance. The instance will be named "default"
$ vespa config set application my-tenant.my-application

# Set application with a specific instance
$ vespa config set application my-tenant.my-application.my-instance

# Set the instance explicitly. This will take precedence over an instance specified as part of the application option.
$ vespa config set instance other-instance

# Set an option in local configuration, for the current application only
$ vespa config set --local wait 600
`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			config := cli.config
			if localArg {
				// Need an application package in working directory to allow local configuration
				if _, err := cli.applicationPackageFrom(nil, false); err != nil {
					return fmt.Errorf("failed to write local configuration: %w", err)
				}
				config = cli.config.local
			}
			if err := config.set(args[0], args[1]); err != nil {
				return err
			}
			return config.write()
		},
	}
	cmd.Flags().BoolVarP(&localArg, "local", "l", false, "Write option to local configuration, i.e. for the current application")
	return cmd
}

func newConfigUnsetCmd(cli *CLI) *cobra.Command {
	var localArg bool
	cmd := &cobra.Command{
		Use:   "unset option-name",
		Short: "Unset a configuration option.",
		Long: `Unset a configuration option.

Unsetting a configuration option will reset it to its default value, which may be empty.
`,
		Example: `# Reset target to its default value
$ vespa config unset target

# Stop overriding application option in local config
$ vespa config unset --local application
`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			config := cli.config
			if localArg {
				if _, err := cli.applicationPackageFrom(nil, false); err != nil {
					return fmt.Errorf("failed to write local configuration: %w", err)
				}
				config = cli.config.local
			}
			if err := config.unset(args[0]); err != nil {
				return err
			}
			return config.write()
		},
	}
	cmd.Flags().BoolVarP(&localArg, "local", "l", false, "Unset option in local configuration, i.e. for the current application")
	return cmd
}

func newConfigGetCmd(cli *CLI) *cobra.Command {
	var localArg bool
	cmd := &cobra.Command{
		Use:   "get [option-name]",
		Short: "Show given configuration option, or all configuration options",
		Long: `Show given configuration option, or all configuration options.

By default this command prints the effective configuration for the current
application, i.e. it takes into account any local configuration located in
[working-directory]/.vespa.
`,
		Example: `$ vespa config get
$ vespa config get target
$ vespa config get --local
`,
		Args:              cobra.MaximumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			config := cli.config
			if localArg {
				if cli.config.local.isEmpty() {
					cli.printWarning("no local configuration present")
					return nil
				}
				config = cli.config.local
			}
			if len(args) == 0 { // Print all values
				for _, option := range config.list(!localArg) {
					config.printOption(option)
				}
			} else {
				return config.printOption(args[0])
			}
			return nil
		},
	}
	cmd.Flags().BoolVarP(&localArg, "local", "l", false, "Show only local configuration, if any")
	return cmd
}

type Config struct {
	homeDir     string
	cacheDir    string
	environment map[string]string
	local       *Config

	flags  map[string]*pflag.Flag
	config *config.Config
}

type KeyPair struct {
	KeyPair         tls.Certificate
	CertificateFile string
	PrivateKeyFile  string
}

func loadConfig(environment map[string]string, flags map[string]*pflag.Flag) (*Config, error) {
	home, err := vespaCliHome(environment)
	if err != nil {
		return nil, fmt.Errorf("could not detect config directory: %w", err)
	}
	config, err := loadConfigFrom(home, environment, flags)
	if err != nil {
		return nil, err
	}
	// Load local config from working directory by default
	if err := config.loadLocalConfigFrom("."); err != nil {
		return nil, err
	}
	return config, nil
}

func loadConfigFrom(dir string, environment map[string]string, flags map[string]*pflag.Flag) (*Config, error) {
	cacheDir, err := vespaCliCacheDir(environment)
	if err != nil {
		return nil, fmt.Errorf("could not detect cache directory: %w", err)
	}
	c := &Config{
		homeDir:     dir,
		cacheDir:    cacheDir,
		environment: environment,
		flags:       flags,
	}
	f, err := os.Open(filepath.Join(dir, configFile))
	var cfg *config.Config
	if os.IsNotExist(err) {
		cfg = config.New()
	} else if err != nil {
		return nil, err
	} else {
		defer f.Close()
		cfg, err = config.Read(f)
		if err != nil {
			return nil, err
		}
	}
	c.config = cfg
	return c, nil
}

func athenzPath(filename string) (string, error) {
	userHome, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(userHome, ".athenz", filename), nil
}

func (c *Config) loadLocalConfigFrom(parent string) error {
	home := filepath.Join(parent, ".vespa")
	_, err := os.Stat(home)
	if err != nil && !os.IsNotExist(err) {
		return err
	}
	config, err := loadConfigFrom(home, c.environment, c.flags)
	if err != nil {
		return err
	}
	c.local = config
	return nil
}

func (c *Config) write() error {
	if err := os.MkdirAll(c.homeDir, 0700); err != nil {
		return err
	}
	configFile := filepath.Join(c.homeDir, configFile)
	return c.config.WriteFile(configFile)
}

func (c *Config) targetType() (string, error) {
	targetType, ok := c.get(targetFlag)
	if !ok {
		return "", fmt.Errorf("target is unset")
	}
	return targetType, nil
}

func (c *Config) timeout() (time.Duration, error) {
	wait, ok := c.get(waitFlag)
	if !ok {
		return 0, nil
	}
	secs, err := strconv.Atoi(wait)
	if err != nil {
		return 0, err
	}
	return time.Duration(secs) * time.Second, nil
}

func (c *Config) isQuiet() bool {
	quiet, _ := c.get(quietFlag)
	return quiet == "true"
}

func (c *Config) application() (vespa.ApplicationID, error) {
	app, ok := c.get(applicationFlag)
	if !ok {
		return vespa.ApplicationID{}, errHint(fmt.Errorf("no application specified"), "Try the --"+applicationFlag+" flag")
	}
	application, err := vespa.ApplicationFromString(app)
	if err != nil {
		return vespa.ApplicationID{}, errHint(err, "application format is <tenant>.<app>[.<instance>]")
	}
	instance, ok := c.get(instanceFlag)
	if ok {
		application.Instance = instance
	}
	return application, nil
}

func (c *Config) cluster() string {
	cluster, _ := c.get(clusterFlag)
	return cluster
}

func (c *Config) deploymentIn(system vespa.System) (vespa.Deployment, error) {
	zone := system.DefaultZone
	zoneName, ok := c.get(zoneFlag)
	if ok {
		var err error
		zone, err = vespa.ZoneFromString(zoneName)
		if err != nil {
			return vespa.Deployment{}, err
		}
	}
	app, err := c.application()
	if err != nil {
		return vespa.Deployment{}, err
	}
	return vespa.Deployment{System: system, Application: app, Zone: zone}, nil
}

func (c *Config) certificatePath(app vespa.ApplicationID, targetType string) (string, error) {
	if override, ok := c.environment["VESPA_CLI_DATA_PLANE_CERT_FILE"]; ok {
		return override, nil
	}
	if targetType == vespa.TargetHosted {
		return athenzPath("cert")
	}
	return c.applicationFilePath(app, "data-plane-public-cert.pem")
}

func (c *Config) privateKeyPath(app vespa.ApplicationID, targetType string) (string, error) {
	if override, ok := c.environment["VESPA_CLI_DATA_PLANE_KEY_FILE"]; ok {
		return override, nil
	}
	if targetType == vespa.TargetHosted {
		return athenzPath("key")
	}
	return c.applicationFilePath(app, "data-plane-private-key.pem")
}

func (c *Config) x509KeyPair(app vespa.ApplicationID, targetType string) (KeyPair, error) {
	cert, certOk := c.environment["VESPA_CLI_DATA_PLANE_CERT"]
	key, keyOk := c.environment["VESPA_CLI_DATA_PLANE_KEY"]
	var (
		kp       tls.Certificate
		err      error
		certFile string
		keyFile  string
	)
	if certOk && keyOk {
		// Use key pair from environment
		kp, err = tls.X509KeyPair([]byte(cert), []byte(key))
	} else {
		keyFile, err = c.privateKeyPath(app, targetType)
		if err != nil {
			return KeyPair{}, err
		}
		certFile, err = c.certificatePath(app, targetType)
		if err != nil {
			return KeyPair{}, err
		}
		kp, err = tls.LoadX509KeyPair(certFile, keyFile)
	}
	if err != nil {
		return KeyPair{}, err
	}
	if targetType == vespa.TargetHosted {
		cert, err := x509.ParseCertificate(kp.Certificate[0])
		if err != nil {
			return KeyPair{}, err
		}
		now := time.Now()
		expiredAt := cert.NotAfter
		if expiredAt.Before(now) {
			delta := now.Sub(expiredAt).Truncate(time.Second)
			return KeyPair{}, fmt.Errorf("certificate %s expired at %s (%s ago)", certFile, cert.NotAfter, delta)
		}
		return KeyPair{KeyPair: kp, CertificateFile: certFile, PrivateKeyFile: keyFile}, nil
	}
	return KeyPair{
		KeyPair:         kp,
		CertificateFile: certFile,
		PrivateKeyFile:  keyFile,
	}, nil
}

func (c *Config) apiKeyFileFromEnv() (string, bool) {
	override, ok := c.environment["VESPA_CLI_API_KEY_FILE"]
	return override, ok
}

func (c *Config) apiKeyFromEnv() ([]byte, bool) {
	override, ok := c.environment["VESPA_CLI_API_KEY"]
	return []byte(override), ok
}

func (c *Config) apiKeyPath(tenantName string) string {
	if override, ok := c.apiKeyFileFromEnv(); ok {
		return override
	}
	return filepath.Join(c.homeDir, tenantName+".api-key.pem")
}

func (c *Config) authConfigPath() string {
	return filepath.Join(c.homeDir, "auth.json")
}

func (c *Config) readAPIKey(cli *CLI, system vespa.System, tenantName string) ([]byte, error) {
	if override, ok := c.apiKeyFromEnv(); ok {
		return override, nil
	}
	if path, ok := c.apiKeyFileFromEnv(); ok {
		return os.ReadFile(path)
	}
	if cli.isCloudCI() {
		return nil, nil // Vespa Cloud CI only talks to data plane and does not have an API key
	}
	if !cli.isCI() {
		client, err := auth0.New(c.authConfigPath(), system.Name, system.URL)
		if err == nil && client.HasCredentials() {
			return nil, nil // use Auth0
		}
		cli.printWarning("Authenticating with API key. This is discouraged in non-CI environments", "Authenticate with 'vespa auth login'")
	}
	return os.ReadFile(c.apiKeyPath(tenantName))
}

func (c *Config) readSessionID(app vespa.ApplicationID) (int64, error) {
	sessionPath, err := c.applicationFilePath(app, "session_id")
	if err != nil {
		return 0, err
	}
	b, err := os.ReadFile(sessionPath)
	if err != nil {
		return 0, err
	}
	return strconv.ParseInt(strings.TrimSpace(string(b)), 10, 64)
}

func (c *Config) writeSessionID(app vespa.ApplicationID, sessionID int64) error {
	sessionPath, err := c.applicationFilePath(app, "session_id")
	if err != nil {
		return err
	}
	return os.WriteFile(sessionPath, []byte(fmt.Sprintf("%d\n", sessionID)), 0600)
}

func (c *Config) applicationFilePath(app vespa.ApplicationID, name string) (string, error) {
	appDir := filepath.Join(c.homeDir, app.String())
	if err := os.MkdirAll(appDir, 0700); err != nil {
		return "", err
	}
	return filepath.Join(appDir, name), nil
}

func (c *Config) isEmpty() bool { return len(c.config.Keys()) == 0 }

// list returns the options that have been set in this configuration. If includeUnset is true, also return options that
// haven't been set.
func (c *Config) list(includeUnset bool) []string {
	if !includeUnset {
		return c.config.Keys()
	}
	var flags []string
	for k := range c.flags {
		flags = append(flags, k)
	}
	sort.Strings(flags)
	return flags
}

// flagValue returns the set value and default value of the named flag.
func (c *Config) flagValue(name string) (string, string) {
	f, ok := c.flags[name]
	if !ok {
		return "", ""
	}
	return f.Value.String(), f.DefValue
}

// getNonEmpty returns value of given option, if that value is non-empty
func (c *Config) getNonEmpty(option string) (string, bool) {
	v, ok := c.config.Get(option)
	if v == "" {
		return "", false
	}
	return v, ok
}

// get returns the value associated with option, from the most preferred source in the following order: flag > local
// config > global config.
func (c *Config) get(option string) (string, bool) {
	flagValue, flagDefault := c.flagValue(option)
	// explicit flag value always takes precedence over everything else
	if flagValue != flagDefault {
		return flagValue, true
	}
	// ... then local config, if option is explicitly defined there
	if c.local != nil {
		if value, ok := c.local.getNonEmpty(option); ok {
			return value, ok
		}
	}
	// ... then global config
	if v, ok := c.getNonEmpty(option); ok {
		return v, ok
	}
	// ... then finally default flag value, if any
	return flagDefault, flagDefault != ""
}

func (c *Config) set(option, value string) error {
	switch option {
	case targetFlag:
		switch value {
		case vespa.TargetLocal, vespa.TargetCloud, vespa.TargetHosted:
			c.config.Set(option, value)
			return nil
		}
		if strings.HasPrefix(value, "http://") || strings.HasPrefix(value, "https://") {
			c.config.Set(option, value)
			return nil
		}
	case applicationFlag:
		app, err := vespa.ApplicationFromString(value)
		if err != nil {
			return err
		}
		c.config.Set(option, app.String())
		return nil
	case instanceFlag:
		c.config.Set(option, value)
		return nil
	case clusterFlag:
		c.config.Set(clusterFlag, value)
		return nil
	case waitFlag:
		if n, err := strconv.Atoi(value); err != nil || n < 0 {
			return fmt.Errorf("%s option must be an integer >= 0, got %q", option, value)
		}
		c.config.Set(option, value)
		return nil
	case colorFlag:
		switch value {
		case "auto", "never", "always":
			c.config.Set(option, value)
			return nil
		}
	case quietFlag:
		switch value {
		case "true", "false":
			c.config.Set(option, value)
			return nil
		}
	case zoneFlag:
		if _, err := vespa.ZoneFromString(value); err != nil {
			return err
		}
		c.config.Set(option, value)
		return nil
	}
	return fmt.Errorf("invalid option or value: %s = %s", option, value)
}

func (c *Config) unset(option string) error {
	if err := c.checkOption(option); err != nil {
		return err
	}
	c.config.Del(option)
	return nil
}

func (c *Config) checkOption(option string) error {
	if _, ok := c.flags[option]; !ok {
		return fmt.Errorf("invalid option: %s", option)
	}
	return nil
}

func (c *Config) printOption(option string) error {
	if err := c.checkOption(option); err != nil {
		return err
	}
	value, ok := c.get(option)
	if !ok {
		faintColor := color.New(color.FgWhite, color.Faint)
		value = faintColor.Sprint("<unset>")
	} else {
		value = color.CyanString(value)
	}
	log.Printf("%s = %s", option, value)
	return nil
}

func vespaCliHome(env map[string]string) (string, error) {
	home := env["VESPA_CLI_HOME"]
	if home == "" {
		userHome, err := os.UserHomeDir()
		if err != nil {
			return "", err
		}
		home = filepath.Join(userHome, ".vespa")
	}
	if err := os.MkdirAll(home, 0700); err != nil {
		return "", err
	}
	return home, nil
}

func vespaCliCacheDir(env map[string]string) (string, error) {
	cacheDir := env["VESPA_CLI_CACHE_DIR"]
	if cacheDir == "" {
		userCacheDir, err := os.UserCacheDir()
		if err != nil {
			return "", err
		}
		cacheDir = filepath.Join(userCacheDir, "vespa")
	}
	if err := os.MkdirAll(cacheDir, 0755); err != nil {
		return "", err
	}
	return cacheDir, nil
}
