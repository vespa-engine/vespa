// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa init command
// author: bratseth

package cmd

import (
    "archive/zip"
    "errors"
    "path/filepath"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "io"
    "io/ioutil"
    "net/http"
    "net/url"
    "os"
    "strings"
    "time"
)

// Set this to test without downloading this file from github
var existingSampleAppsZip string

func init() {
    existingSampleAppsZip = ""
    rootCmd.AddCommand(initCmd)
}

var initCmd = &cobra.Command{
    // TODO: "application" and "list" subcommands?
    Use:   "init applicationName source",
    Short: "Creates the files and directory structure for a new Vespa application",
    Long:  `TODO`,
    Args: func(cmd *cobra.Command, args []string) error {
        if len(args) != 2 {
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
    }

	zipReader, zipOpenError := zip.OpenReader(zipFile.Name())
	if zipOpenError != nil {
        utils.Error("Could not open sample apps zip '" + zipFile.Name() + "'")
        utils.Detail(zipOpenError.Error())
	}
	defer zipReader.Close()

    found := false
    for _, f := range zipReader.File {
        zipEntryPrefix := "sample-apps-master/" + source + "/"
	    if strings.HasPrefix(f.Name, zipEntryPrefix) {
 	        found = true
	        copyError := copy(f, name, zipEntryPrefix)
	        if copyError != nil {
                utils.Error("Could not copy zip entry '" + f.Name + "' to " + name)
                utils.Detail(copyError.Error())
                return
	        }
        }
	}
	if !found {
	    utils.Error("Could not find source application '" + source + "'")
	} else {
        utils.Success("Created " + name)
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

func copy(f *zip.File, destinationDir string, zipEntryPrefix string) error {
    destinationPath := filepath.Join(destinationDir, filepath.FromSlash(strings.TrimPrefix(f.Name, zipEntryPrefix)))
    if strings.HasSuffix(f.Name, "/") {
        if f.Name != zipEntryPrefix { // root is already created
            createError := os.Mkdir(destinationPath, 0755)
            if createError != nil {
                return createError
            }
        }
    } else {
        zipEntry, zipEntryOpenError := f.Open()
        if zipEntryOpenError != nil {
            return zipEntryOpenError
        }
        defer zipEntry.Close()

        destination, createError := os.Create(destinationPath)
        if createError != nil {
            return createError
        }

        _, copyError := io.Copy(destination, zipEntry)
        if copyError != nil {
            return copyError
        }
    }
    return nil
}
