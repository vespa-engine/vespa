// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
    "fmt"
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
    	//viper.BindPFlag("container-target", rootCmd.PersistentFlags().Lookup("container-target"))
    	//viper.SetDefault("container-target", "http://127.0.0.1:8080")
    },
}

func initConfig() {
    home, err := os.UserHomeDir()
    configName := ".vespa"
    configType := "yaml"

    cobra.CheckErr(err)
    viper.AddConfigPath(home)
    viper.SetConfigType(configType)
    viper.SetConfigName(configName)
	viper.AutomaticEnv()

    // Viper bug: WriteConfig() will not create the file if missing
    configPath := filepath.Join(home, configName + "." + configType)
    _, statErr := os.Stat(configPath)
    if !os.IsExist(statErr) {
        fmt.Println("Creating blank config file")
        if _, createErr := os.Create(configPath); createErr != nil {
            utils.Error("Warning: Can not remember flag parameters: " + createErr.Error())
        }
    }

	err2 := viper.WriteConfig()
	if err2 != nil {
	    fmt.Println("WriteConfig err:", err2.Error())
	} else {
	    fmt.Println("No WriteConfig error")
	}

	if err := viper.ReadInConfig(); err == nil {
		fmt.Println("Using config file:", viper.ConfigFileUsed())
	} else {
		fmt.Println("Not using config file:", err.Error())
	}
}

