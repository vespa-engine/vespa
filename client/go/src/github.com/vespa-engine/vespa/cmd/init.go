// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa init command
// author: bratseth

package cmd

import (
    "archive/zip"
    "errors"
    "fmt"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "io"
    "io/ioutil"
    "net/http"
    "net/url"
    "os"
    "time"
)

// Set this to test without downloading this file from github
var existingSampleAppsZip string

func init() {
    existingSampleAppsZip = ""
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
    zipFile := getSampleAppsZip()
    if zipFile == nil {
        return
    }
    if existingSampleAppsZip == "" { // Indicates we created a temp file now
        defer os.Remove(zipFile.Name())
    }

    createErr := os.Mkdir(name, 0755)
    if createErr != nil {
        utils.Error("Could not create directory '" + name + "'")
        utils.Detail(createErr.Error())
        return
    } else {
        utils.Success("Created " + name)
    }

	zipReader, zipOpenError := zip.OpenReader(zipFile.Name())
	if zipOpenError != nil {
        utils.Error("Could not open sample apps zip '" + zipFile.Name() + "'")
        utils.Detail(zipOpenError.Error())
	}
	defer zipReader.Close()

    fmt.Println("Reading zip ...")
	for _, f := range zipReader.File {
		fmt.Println("Entry:", f.Name)
		rc, err := f.Open()
		if err != nil {
		    utils.Error(err.Error())
		}
		// _, err = io.CopyN(os.Stdout, rc, 68)
		if err != nil {
		    utils.Error(err.Error())
		}
		rc.Close()
	}
}

func getSampleAppsZip() *os.File {
    if existingSampleAppsZip != "" {
        existing, openExistingError := os.Open(existingSampleAppsZip)
        if openExistingError != nil {
            utils.Error("Could not open existing sample apps zip file '" + existingSampleAppsZip + "'")
            utils.Detail(openExistingError.Error())
        }
        return existing
    }

    // TODO: Cache it?
    utils.Detail("Downloading sample apps ...") // TODO: Spawn thread to indicate progress
    zipUrl, _ := url.Parse("https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip")
    request := &http.Request{
        URL: zipUrl,
        Method: "GET",
    }
    response := utils.HttpDo(request, time.Minute * 60, "GitHub")
    defer response.Body.Close()
    if response.StatusCode != 200 {
        utils.Error("Could not download sample apps from github")
        utils.Detail(response.Status)
        return nil
    }

    destination, tempFileError := ioutil.TempFile("", "prefix")
    if tempFileError != nil {
        utils.Error("Could not create a temp file to hold sample apps")
        utils.Detail(tempFileError.Error())
    }
    // destination, _ := os.Create("./" + name + "/sample-apps.zip")
    // defer destination.Close()
    _, err := io.Copy(destination, response.Body)
    if err != nil {
        utils.Error("Could not download sample apps from GitHub")
        utils.Detail(err.Error())
        return nil
    }
    return destination
}
