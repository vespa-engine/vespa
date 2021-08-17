// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa init command
// author: bratseth

package cmd

import (
    "errors"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "io"
    "net/http"
    "net/url"
    "os"
    "time"
)

func init() {
    rootCmd.AddCommand(initCmd)
}

var initCmd = &cobra.Command{
    Use:   "init applicationName source",
    Short: "Creates the files and directory structure for a new Vespa application",
    Long:  `TODO`,
    Args: func(cmd *cobra.Command, args []string) error {
        if len(args) != 2 {
            // TODO: Support creating an "empty" application by not specifying a source
            return errors.New("vespa init requires a project name and source")
        }
        return nil
    },
    Run: func(cmd *cobra.Command, args []string) {
        initApplication(args[0], args[1])
    },
}

func initApplication(name string, source string) {
    createErr := os.Mkdir(name, 0755)
    if createErr != nil {
        utils.Error("Could not create directory '" + name + "'")
        utils.Detail(createErr.Error())
    }

    zipUrl, _ := url.Parse("https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip")
    request := &http.Request{
        URL: zipUrl,
        Method: "GET",
    }
    response := utils.HttpDo(request, time.Minute * 60, "GitHub")
    if response.StatusCode != 200 {
        utils.Error("Could not download sample apps from github")
        utils.Detail(response.Status)
    }
    defer response.Body.Close()

    destination, _ := os.Create("./" + name + "/sample-apps.zip") // TODO: Path
    defer destination.Close()
    _, err := io.Copy(destination, response.Body)
    if err != nil {
        utils.Error("Could not download sample apps from GitHub")
        utils.Detail(err.Error())
        return
    }
    utils.Success("Downloaded zip, possibly")
}
