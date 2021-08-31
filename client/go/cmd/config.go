// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa config command
// author: bratseth

package cmd

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"github.com/vespa-engine/vespa/util"
)

const (
	configName = "config"
	configType = "yaml"
)

func init() {
	rootCmd.AddCommand(configCmd)
	configCmd.AddCommand(setConfigCmd)
	configCmd.AddCommand(getConfigCmd)
}

var configCmd = &cobra.Command{
	Use:   "config",
	Short: "Configure the Vespa command",
	Long:  "Get and set options for Vespa commands. This is an alternative to always specifying flags",
	Run: func(cmd *cobra.Command, args []string) {
		// Root command does nothing
		cmd.Help()
		os.Exit(1)
	},
}

var setConfigCmd = &cobra.Command{
	Use:     "set option value",
	Short:   "Set a configuration option.",
	Example: "vespa config set target cloud",
	Args:    cobra.ExactArgs(2),
	Run: func(cmd *cobra.Command, args []string) {
		if err := setOption(args[0], args[1]); err != nil {
			log.Print(err)
		} else {
			writeConfig()
		}
	},
}

var getConfigCmd = &cobra.Command{
	Use:     "get option",
	Short:   "Get a configuration option",
	Example: "vespa config get target",
	Args:    cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		option := args[0]
		value, err := getOption(option)
		if err != nil {
			value = color.Faint("<unset>").String()
		} else {
			value = color.Cyan(value).String()
		}
		log.Printf("%s = %s", option, value)
	},
}

func configDir(application string) (string, error) {
	home := os.Getenv("VESPA_CLI_HOME")
	if home == "" {
		var err error
		home, err = os.UserHomeDir()
		if err != nil {
			return "", err
		}
	}
	return filepath.Join(home, ".vespa", application), nil
}

func bindFlagToConfig(option string, command *cobra.Command) {
	viper.BindPFlag(option, command.PersistentFlags().Lookup(option))
}

func readConfig() {
	configDir, err := configDir("")
	cobra.CheckErr(err)

	viper.SetConfigName(configName)
	viper.SetConfigType(configType)
	viper.AddConfigPath(configDir)
	viper.AutomaticEnv()
	viper.BindPFlag("target", deployCmd.PersistentFlags().Lookup("target"))

	err = viper.ReadInConfig()
	if _, ok := err.(viper.ConfigFileNotFoundError); ok {
		return // Fine
	}
	cobra.CheckErr(err)
}

func getOption(option string) (string, error) {
	value := viper.GetString(option)
	if value == "" {
		return "", fmt.Errorf("no such option: %q", option)
	}
	return value, nil
}

func setOption(option, value string) error {
	if option != "target" {
		return fmt.Errorf("invalid option: %q", option)
	}
	switch value {
	case "local", "cloud":
		viper.Set(option, value)
		return nil
	}
	if strings.HasPrefix(value, "http://") || strings.HasPrefix(value, "https://") {
		viper.Set(option, value)
		return nil
	}
	return fmt.Errorf("invalid value for option %q: %q", option, value)
}

func writeConfig() {
	configDir, err := configDir("")
	if err != nil {
		log.Fatalf("Could not decide config directory: %s", color.Red(err))
	}

	if !util.PathExists(configDir) {
		if err := os.MkdirAll(configDir, 0700); err != nil {
			log.Fatalf("Could not create %s: %s", color.Cyan(configDir), color.Red(err))
		}
	}

	configFile := filepath.Join(configDir, configName+"."+configType)
	if !util.PathExists(configFile) {
		if _, err := os.Create(configFile); err != nil {
			log.Fatalf("Could not create %s: %s", color.Cyan(configFile), color.Red(err))
		}
	}

	if err := viper.WriteConfig(); err != nil {
		log.Printf("Could not write config: %s", color.Red(err))
	}
}
