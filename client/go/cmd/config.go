// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa config command
// author: bratseth

package cmd

import (
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
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
	Use:               "config",
	Short:             "Configure default values for flags",
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		// Root command does nothing
		cmd.Help()
		os.Exit(1)
	},
}

var setConfigCmd = &cobra.Command{
	Use:               "set option-name value",
	Short:             "Set a configuration option.",
	Example:           "$ vespa config set target cloud",
	DisableAutoGenTag: true,
	Args:              cobra.ExactArgs(2),
	Run: func(cmd *cobra.Command, args []string) {
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}
		if err := cfg.Set(args[0], args[1]); err != nil {
			fatalErr(err)
		} else {
			if err := cfg.Write(); err != nil {
				fatalErr(err)
			}
		}
	},
}

var getConfigCmd = &cobra.Command{
	Use:               "get option-name",
	Short:             "Get a configuration option",
	Example:           "$ vespa config get target",
	Args:              cobra.MaximumNArgs(1),
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}

		if len(args) == 0 { // Print all values
			printOption(cfg, targetFlag)
			printOption(cfg, applicationFlag)
		} else {
			printOption(cfg, args[0])
		}
	},
}

type Config struct {
	Home       string
	createDirs bool
}

func LoadConfig() (*Config, error) {
	home := os.Getenv("VESPA_CLI_HOME")
	if home == "" {
		var err error
		home, err = os.UserHomeDir()
		if err != nil {
			return nil, err
		}
		home = filepath.Join(home, ".vespa")
	}
	if err := os.MkdirAll(home, 0700); err != nil {
		return nil, err
	}
	c := &Config{Home: home, createDirs: true}
	if err := c.load(); err != nil {
		return nil, err
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
	return c.applicationFilePath(app, "data-plane-public-cert.pem")
}

func (c *Config) PrivateKeyPath(app vespa.ApplicationID) (string, error) {
	return c.applicationFilePath(app, "data-plane-private-key.pem")
}

func (c *Config) APIKeyPath(tenantName string) string {
	return filepath.Join(c.Home, tenantName+".api-key.pem")
}

func (c *Config) ReadAPIKey(tenantName string) ([]byte, error) {
	return ioutil.ReadFile(c.APIKeyPath(tenantName))
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
