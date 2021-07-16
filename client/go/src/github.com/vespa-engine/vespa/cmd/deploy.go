// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
)

func init() {
    rootCmd.AddCommand(deployCmd)
}

var deployCmd = &cobra.Command{
    Use:   "deploy application-package-dir OR application.zip",
    Short: "Deploys an application package",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        deploy()
    },
}

func deploy() {
    // (cd src/main/application && zip -r - .) | \
    //     curl --header Content-Type:application/zip --data-binary @- \
    //     localhost:19071/application/v2/tenant/default/prepareandactivate
    utils.HttpRequest("http://127.0.0.1:19071", "/application/v2/tenant/default/prepareandactivate", "Config server")
}

