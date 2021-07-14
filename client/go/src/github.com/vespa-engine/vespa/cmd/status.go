package cmd

import (
    "bufio"
    "github.com/spf13/cobra"
    "fmt"
    "net/http"
    "strings"
    "time"
)

func init() {
  rootCmd.AddCommand(statusCmd)
  statusCmd.AddCommand(statusConfigServerCmd)
}

var statusCmd = &cobra.Command{
  Use:   "status",
  Short: "Verifies that your Vespa endpoint is ready to use",
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
    response := request(host, path, description)
    if (response == nil) {
        return
    }

    if response.StatusCode != 200 {
        printError(description, "at", host, "is not ready")
        printDetail("Response status:", response.Status)
    } else {
        printSuccess(description, "at", host, "is ready")
    }
}

func request(host string, path string, description string) (response *http.Response) {
    client := &http.Client{
	    Timeout: time.Second * 10,
    }
    response, error := client.Get(host + path)
    if error != nil {
        printError("Could not connect to", strings.ToLower(description), "at", host)
        printDetail(error.Error())
        return
    }
    defer response.Body.Close()

    scanner := bufio.NewScanner(response.Body)

    if error := scanner.Err(); error != nil {
        printError("Error reading data from", strings.ToLower(description), "at", host)
        printDetail(error.Error())
        return nil
    } else {
        return response
    }
}

func printError(messages ...string) {
    print("\033[31m", messages)
}

func printSuccess(messages ...string) {
    print("\033[32m", messages)
}

func printDetail(messages ...string) {
    print("\033[33m", messages)
}

func print(prefix string, messages []string) {
   fmt.Print(prefix)
    for i := 0; i < len(messages); i++ {
        fmt.Print(messages[i])
        fmt.Print(" ")
    }
    fmt.Println("")
}
