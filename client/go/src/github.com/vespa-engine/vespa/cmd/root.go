// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Root Cobra command: vespa
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

var (
	// flags
	targetArgument string

	rootCmd = &cobra.Command{
		Use:   "vespa",
		Short: "A command-line tool for working with Vespa instances",
		Long: `TO
DO`,
	}
)

func init() {
	cobra.OnInitialize(initConfig)

	rootCmd.PersistentFlags().StringVarP(&targetArgument, "target", "t", "local", "The name or URL of the recipient of this command")
	//viper.BindPFlag("container-target", rootCmd.PersistentFlags().Lookup("container-target"))
	//viper.SetDefault("container-target", "http://127.0.0.1:8080")
}

// Execute executes the root command.
func Execute() error {
	err := rootCmd.Execute()
	return err
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
