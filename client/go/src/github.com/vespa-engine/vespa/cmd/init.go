// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa init command
// author: bratseth

package cmd

import (
    "errors"
    "gopkg.in/src-d/go-git.v4"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "io"
    "net/http"
    "net/url"
    "os"
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
    // TODO: Support third-party full github url sources
    //err := os.Mkdir(name, 0755)
    //if err != nil {
    //    utils.Error("Could not create directory '" + name + "'")
    //    utils.Detail(err.Error())
    //}
    zipUrl, _ := url.Parse("https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip")
    request := &http.Request{
        URL: zipUrl,
        Method: "GET",
    }
    response := utils.HttpDoWithoutReadingData(request, "GitHub")
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

func gitStuff(name string, source string) {
    _, err := git.PlainClone("./" + name, false, &git.CloneOptions{ // TODO: Path
        URL: "https://github.com/vespa-engine/sample-apps",
        Progress: os.Stdout,
        Depth: 1,
    })
    if err != nil {
        utils.Error("Could not clone repo in '" + source + "'")
        utils.Detail(err.Error())
    } else {
       utils.Success("Initialized to " + name)
    }
}
