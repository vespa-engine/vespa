package cmd

import (
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
)

func init() {
   rootCmd.AddCommand(statusCmd)
    statusCmd.AddCommand(statusContainerCmd)
    statusCmd.AddCommand(statusConfigServerCmd)
}

var statusCmd = &cobra.Command{
    Use:   "status",
    Short: "Verifies that your Vespa endpoint is ready to use (container by default)",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        status("http://127.0.0.1:8080", "container")
    },
}

var statusContainerCmd = &cobra.Command{
    Use:   "container",
    Short: "Verifies that your Vespa container endpoint is ready [Default]",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        status("http://127.0.0.1:8080", "container")
    },
}

var statusConfigServerCmd = &cobra.Command{
    Use:   "config-server",
    Short: "Verifies that your Vespa config server endpoint is ready",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        status("http://127.0.0.1:19071", "Config server")
    },
}

func status(host string, description string) {
    path := "/ApplicationStatus"
    response := utils.HttpRequest(host, path, description)
    if (response == nil) {
        return
    }

    if response.StatusCode != 200 {
        utils.Error(description, "at", host, "is not ready")
        utils.Detail("Response status:", response.Status)
    } else {
        utils.Success(description, "at", host, "is ready")
    }
}
