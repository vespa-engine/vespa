// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa config command
// author: bratseth

package cmd

import (
	"crypto/tls"
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

const (
	configName = "config"
	configType = "yaml"
)

var flagToConfigBindings map[string]*cobra.Command = make(map[string]*cobra.Command)

func init() {
	rootCmd.AddCommand(configCmd)
	configCmd.AddCommand(setConfigCmd)
	configCmd.AddCommand(getConfigCmd)
}

var configCmd = &cobra.Command{
	Use:   "config",
	Short: "Configure persistent values for flags",
	Long: `Configure persistent values for flags.

This command allows setting a persistent value for a given flag. On future
invocations the flag can then be omitted as it is read from the config file
instead.

Configuration is written to $HOME/.vespa by default. This path can be
overridden by setting the VESPA_CLI_HOME environment variable.`,
	DisableAutoGenTag: true,
	SilenceUsage:      false,
	Args:              cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		return fmt.Errorf("invalid command: %s", args[0])
	},
}

var setConfigCmd = &cobra.Command{
	Use:               "set option-name value",
	Short:             "Set a configuration option.",
	Example:           "$ vespa config set target cloud",
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		cfg, err := LoadConfig()
		if err != nil {
			return err
		}
		if err := cfg.Set(args[0], args[1]); err != nil {
			return err
		}
		return cfg.Write()
	},
}

var getConfigCmd = &cobra.Command{
	Use:   "get [option-name]",
	Short: "Show given configuration option, or all configuration options",
	Example: `$ vespa config get
$ vespa config get target`,
	Args:              cobra.MaximumNArgs(1),
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cfg, err := LoadConfig()
		if err != nil {
			return err
		}
		if len(args) == 0 { // Print all values
			var flags []string
			for flag := range flagToConfigBindings {
				flags = append(flags, flag)
			}
			sort.Strings(flags)
			for _, flag := range flags {
				printOption(cfg, flag)
			}
		} else {
			printOption(cfg, args[0])
		}
		return nil
	},
}

type Config struct {
	Home       string
	createDirs bool
}

type KeyPair struct {
	KeyPair         tls.Certificate
	CertificateFile string
	PrivateKeyFile  string
}

func LoadConfig() (*Config, error) {
	home, err := vespaCliHome()
	if err != nil {
		return nil, fmt.Errorf("could not detect config directory: %w", err)
	}
	c := &Config{Home: home, createDirs: true}
	if err := c.load(); err != nil {
		return nil, fmt.Errorf("could not load config: %w", err)
	}
	return c, nil
}

func (c *Config) Write() error {
	if err := os.MkdirAll(c.Home, 0700); err != nil {
		return err
	}
	configFile := filepath.Join(c.Home, configName+"."+configType)
	if !util.PathExists(configFile) {
		if _, err := os.Create(configFile); err != nil {
			return err
		}
	}
	return viper.WriteConfig()
}

func (c *Config) CertificatePath(app vespa.ApplicationID) (string, error) {
	if override, ok := os.LookupEnv("VESPA_CLI_DATA_PLANE_CERT_FILE"); ok {
		return override, nil
	}
	return c.applicationFilePath(app, "data-plane-public-cert.pem")
}

func (c *Config) PrivateKeyPath(app vespa.ApplicationID) (string, error) {
	if override, ok := os.LookupEnv("VESPA_CLI_DATA_PLANE_KEY_FILE"); ok {
		return override, nil
	}
	return c.applicationFilePath(app, "data-plane-private-key.pem")
}

func (c *Config) X509KeyPair(app vespa.ApplicationID) (KeyPair, error) {
	cert, certOk := os.LookupEnv("VESPA_CLI_DATA_PLANE_CERT")
	key, keyOk := os.LookupEnv("VESPA_CLI_DATA_PLANE_KEY")
	if certOk && keyOk {
		// Use key pair from environment
		kp, err := tls.X509KeyPair([]byte(cert), []byte(key))
		return KeyPair{KeyPair: kp}, err
	}
	privateKeyFile, err := c.PrivateKeyPath(app)
	if err != nil {
		return KeyPair{}, err
	}
	certificateFile, err := c.CertificatePath(app)
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

func (c *Config) APIKeyPath(tenantName string) string {
	if override, ok := os.LookupEnv("VESPA_CLI_API_KEY_FILE"); ok {
		return override
	}
	return filepath.Join(c.Home, tenantName+".api-key.pem")
}

func (c *Config) ReadAPIKey(tenantName string) ([]byte, error) {
	if override, ok := os.LookupEnv("VESPA_CLI_API_KEY"); ok {
		return []byte(override), nil
	}
	return ioutil.ReadFile(c.APIKeyPath(tenantName))
}

func (c *Config) AuthConfigPath() string {
	return filepath.Join(c.Home, "auth.json")
}

func (c *Config) ReadSessionID(app vespa.ApplicationID) (int64, error) {
	sessionPath, err := c.applicationFilePath(app, "session_id")
	if err != nil {
		return 0, err
	}
	b, err := ioutil.ReadFile(sessionPath)
	if err != nil {
		return 0, err
	}
	return strconv.ParseInt(strings.TrimSpace(string(b)), 10, 64)
}

func (c *Config) WriteSessionID(app vespa.ApplicationID, sessionID int64) error {
	sessionPath, err := c.applicationFilePath(app, "session_id")
	if err != nil {
		return err
	}
	return ioutil.WriteFile(sessionPath, []byte(fmt.Sprintf("%d\n", sessionID)), 0600)
}

func (c *Config) applicationFilePath(app vespa.ApplicationID, name string) (string, error) {
	appDir := filepath.Join(c.Home, app.String())
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
	viper.AddConfigPath(c.Home)
	viper.AutomaticEnv()
	for option, command := range flagToConfigBindings {
		viper.BindPFlag(option, command.PersistentFlags().Lookup(option))
	}
	err := viper.ReadInConfig()
	if _, ok := err.(viper.ConfigFileNotFoundError); ok {
		return nil
	}
	return err
}

func (c *Config) Get(option string) (string, error) {
	value := viper.GetString(option)
	if value == "" {
		return "", fmt.Errorf("no such option: %q", option)
	}
	return value, nil
}

func (c *Config) Set(option, value string) error {
	switch option {
	case targetFlag:
		switch value {
		case "local", "cloud":
			viper.Set(option, value)
			return nil
		}
		if strings.HasPrefix(value, "http://") || strings.HasPrefix(value, "https://") {
			viper.Set(option, value)
			return nil
		}
	case applicationFlag:
		if _, err := vespa.ApplicationFromString(value); err != nil {
			return err
		}
		viper.Set(option, value)
		return nil
	case waitFlag:
		if _, err := strconv.ParseUint(value, 10, 32); err != nil {
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
	case cloudAuthFlag:
		switch value {
		case "access-token", "api-key":
			viper.Set(option, value)
			return nil
		}
	}
	return fmt.Errorf("invalid option or value: %q: %q", option, value)
}

func printOption(cfg *Config, option string) {
	value, err := cfg.Get(option)
	if err != nil {
		value = color.Faint("<unset>").String()
	} else {
		value = color.Cyan(value).String()
	}
	log.Printf("%s = %s", option, value)
}

func bindFlagToConfig(option string, command *cobra.Command) {
	flagToConfigBindings[option] = command
}
