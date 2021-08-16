// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa config command
// author: bratseth

package cmd

import (
    "github.com/spf13/cobra"
	"github.com/spf13/viper"
    "github.com/vespa-engine/vespa/utils"
    "os"
    "path/filepath"
)

func init() {
    rootCmd.AddCommand(configCmd)
}

var configCmd = &cobra.Command{
    Use:   "config",
    Short: "Configure the Vespa command",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
    },
}

func readConfig() {
    home, err := os.UserHomeDir()
    configName := ".vespa"
    configType := "yaml"

    cobra.CheckErr(err)
    viper.AddConfigPath(home)
    viper.SetConfigType(configType)
    viper.SetConfigName(configName)
	viper.AutomaticEnv()

	viper.ReadInConfig();
}

// WIP: Not used yet
func writeConfig() {
  	//viper.BindPFlag("container-target", rootCmd.PersistentFlags().Lookup("container-target"))
  	//viper.SetDefault("container-target", "http://127.0.0.1:8080")

    home, _ := os.UserHomeDir()
    configName := ".vespa"
    configType := "yaml"

    // Viper bug: WriteConfig() will not create the file if missing
    configPath := filepath.Join(home, configName + "." + configType)
    _, statErr := os.Stat(configPath)
    if !os.IsExist(statErr) {
        if _, createErr := os.Create(configPath); createErr != nil {
            utils.Error("Warning: Can not remember flag parameters: " + createErr.Error())
        }
    }

	writeErr := viper.WriteConfig()
	if writeErr != nil {
	    utils.Error("Could not write config:", writeErr.Error())
	}
}