// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa config command
// author: bratseth

package cmd

import (
	"crypto/tls"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"github.com/vespa-engine/vespa/client/go/auth/auth0"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

const (
	configName = "config"
	configType = "yaml"
)

func newConfigCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "config",
		Short: "Configure persistent values for global flags",
		Long: `Configure persistent values for global flags.

This command allows setting a persistent value for a given global flag. On
future invocations the flag can then be omitted as it is read from the config
file instead.

Configuration is written to $HOME/.vespa by default. This path can be
overridden by setting the VESPA_CLI_HOME environment variable.`,
		DisableAutoGenTag: true,
		SilenceUsage:      false,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}
}

func newConfigSetCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:   "set option-name value",
		Short: "Set a configuration option.",
		Example: `# Set the target to Vespa Cloud
$ vespa config set target cloud

# Set application, without a specific instance. The instance will be named "default"
$ vespa config set application my-tenant.my-application

# Set application with a specific instance
$ vespa config set application my-tenant.my-application.my-instance

# Set the instance explicitly. This will take precedence over an instance specified as part of the application option.
$ vespa config set instance other-instance`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := cli.config.set(args[0], args[1]); err != nil {
				return err
			}
			return cli.config.write()
		},
	}
}

func newConfigGetCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:   "get [option-name]",
		Short: "Show given configuration option, or all configuration options",
		Example: `$ vespa config get
$ vespa config get target`,
		Args:              cobra.MaximumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) == 0 { // Print all values
				var flags []string
				for flag := range cli.config.bindings.flag {
					flags = append(flags, flag)
				}
				sort.Strings(flags)
				for _, flag := range flags {
					cli.config.printOption(flag)
				}
			} else {
				cli.config.printOption(args[0])
			}
			return nil
		},
	}
}

type Config struct {
	homeDir     string
	cacheDir    string
	environment map[string]string
	bindings    ConfigBindings
	createDirs  bool
}

type ConfigBindings struct {
	flag        map[string]*cobra.Command
	environment map[string]string
}

type KeyPair struct {
	KeyPair         tls.Certificate
	CertificateFile string
	PrivateKeyFile  string
}

func NewConfigBindings() ConfigBindings {
	return ConfigBindings{
		flag:        make(map[string]*cobra.Command),
		environment: make(map[string]string),
	}
}

func (b *ConfigBindings) bindFlag(name string, command *cobra.Command) {
	b.flag[name] = command
}

func (b *ConfigBindings) bindEnvironment(flagName string, variable string) {
	b.environment[flagName] = variable
}

func loadConfig(environment map[string]string, bindings ConfigBindings) (*Config, error) {
	home, err := vespaCliHome(environment)
	if err != nil {
		return nil, fmt.Errorf("could not detect config directory: %w", err)
	}
	cacheDir, err := vespaCliCacheDir(environment)
	if err != nil {
		return nil, fmt.Errorf("could not detect cache directory: %w", err)
	}
	c := &Config{
		homeDir:     home,
		cacheDir:    cacheDir,
		environment: environment,
		bindings:    bindings,
		createDirs:  true,
	}
	if err := c.load(); err != nil {
		return nil, fmt.Errorf("could not load config: %w", err)
	}
	return c, nil
}

func (c *Config) write() error {
	if err := os.MkdirAll(c.homeDir, 0700); err != nil {
		return err
	}
	configFile := filepath.Join(c.homeDir, configName+"."+configType)
	if !util.PathExists(configFile) {
		if _, err := os.Create(configFile); err != nil {
			return err
		}
	}
	return viper.WriteConfig()
}

func (c *Config) targetType() (string, error) {
	targetType, ok := c.get(targetFlag)
	if !ok {
		return "", fmt.Errorf("target is unset")
	}
	return targetType, nil
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

func (c *Config) deploymentIn(zoneName string, system vespa.System) (vespa.Deployment, error) {
	zone := system.DefaultZone
	var err error
	if zoneName != "" {
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

func (c *Config) certificatePath(app vespa.ApplicationID) (string, error) {
	if override, ok := c.environment["VESPA_CLI_DATA_PLANE_CERT_FILE"]; ok {
		return override, nil
	}
	return c.applicationFilePath(app, "data-plane-public-cert.pem")
}

func (c *Config) privateKeyPath(app vespa.ApplicationID) (string, error) {
	if override, ok := c.environment["VESPA_CLI_DATA_PLANE_KEY_FILE"]; ok {
		return override, nil
	}
	return c.applicationFilePath(app, "data-plane-private-key.pem")
}

func (c *Config) x509KeyPair(app vespa.ApplicationID) (KeyPair, error) {
	cert, certOk := c.environment["VESPA_CLI_DATA_PLANE_CERT"]
	key, keyOk := c.environment["VESPA_CLI_DATA_PLANE_KEY"]
	if certOk && keyOk {
		// Use key pair from environment
		kp, err := tls.X509KeyPair([]byte(cert), []byte(key))
		return KeyPair{KeyPair: kp}, err
	}
	privateKeyFile, err := c.privateKeyPath(app)
	if err != nil {
		return KeyPair{}, err
	}
	certificateFile, err := c.certificatePath(app)
	if err != nil {
		return KeyPair{}, err
	}
	kp, err := tls.LoadX509KeyPair(certificateFile, privateKeyFile)
	if err != nil {
		return KeyPair{}, err
	}
	return KeyPair{
		KeyPair:         kp,
		CertificateFile: certificateFile,
		PrivateKeyFile:  privateKeyFile,
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

func (c *Config) readAPIKey(tenantName string) ([]byte, error) {
	if override, ok := c.apiKeyFromEnv(); ok {
		return override, nil
	}
	return os.ReadFile(c.apiKeyPath(tenantName))
}

// useAPIKey returns true if an API key should be used when authenticating with system.
func (c *Config) useAPIKey(cli *CLI, system vespa.System, tenantName string) bool {
	if _, ok := c.apiKeyFromEnv(); ok {
		return true
	}
	if _, ok := c.apiKeyFileFromEnv(); ok {
		return true
	}
	if !cli.isCI() {
		// Fall back to API key, if present and Auth0 has not been configured
		client, err := auth0.New(c.authConfigPath(), system.Name, system.URL)
		if err != nil || !client.HasCredentials() {
			cli.printWarning("Regular authentication is preferred over API key in a non-CI context", "Authenticate with 'vespa auth login'")
			return util.PathExists(c.apiKeyPath(tenantName))
		}
	}
	return false
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
	if c.createDirs {
		if err := os.MkdirAll(appDir, 0700); err != nil {
			return "", err
		}
	}
	return filepath.Join(appDir, name), nil
}

func (c *Config) load() error {
	viper.SetConfigName(configName)
	viper.SetConfigType(configType)
	viper.AddConfigPath(c.homeDir)
	for option, command := range c.bindings.flag {
		viper.BindPFlag(option, command.PersistentFlags().Lookup(option))
	}
	err := viper.ReadInConfig()
	if _, ok := err.(viper.ConfigFileNotFoundError); ok {
		return nil
	}
	return err
}

func (c *Config) get(option string) (string, bool) {
	if envVar, ok := c.bindings.environment[option]; ok {
		if value, ok := c.environment[envVar]; ok {
			return value, true
		}
	}
	value := viper.GetString(option)
	if value == "" {
		return "", false
	}
	return value, true
}

func (c *Config) set(option, value string) error {
	switch option {
	case targetFlag:
		switch value {
		case vespa.TargetLocal, vespa.TargetCloud, vespa.TargetHosted:
			viper.Set(option, value)
			return nil
		}
		if strings.HasPrefix(value, "http://") || strings.HasPrefix(value, "https://") {
			viper.Set(option, value)
			return nil
		}
	case applicationFlag:
		app, err := vespa.ApplicationFromString(value)
		if err != nil {
			return err
		}
		viper.Set(option, app.String())
		return nil
	case instanceFlag:
		viper.Set(option, value)
		return nil
	case waitFlag:
		if n, err := strconv.Atoi(value); err != nil || n < 0 {
			return fmt.Errorf("%s option must be an integer >= 0, got %q", option, value)
		}
		viper.Set(option, value)
		return nil
	case colorFlag:
		switch value {
		case "auto", "never", "always":
			viper.Set(option, value)
			return nil
		}
	case quietFlag:
		switch value {
		case "true", "false":
			viper.Set(option, value)
			return nil
		}
	}
	return fmt.Errorf("invalid option or value: %q: %q", option, value)
}

func (c *Config) printOption(option string) {
	value, ok := c.get(option)
	if !ok {
		faintColor := color.New(color.FgWhite, color.Faint)
		value = faintColor.Sprint("<unset>")
	} else {
		value = color.CyanString(value)
	}
	log.Printf("%s = %s", option, value)
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
