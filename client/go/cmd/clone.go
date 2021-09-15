// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa clone command
// author: bratseth

package cmd

import (
	"archive/zip"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
)

// Set this to test without downloading this file from github
var existingSampleAppsZip string
var listApps bool

func init() {
	rootCmd.AddCommand(cloneCmd)
	cloneCmd.Flags().BoolVarP(&listApps, "list", "l", false, "List available sample applications")
}

var cloneCmd = &cobra.Command{
	// TODO: "application" and "list" subcommands?
	Use:   "clone sample-application-path target-directory",
	Short: "Create files and directory structure for a new Vespa application from a sample application",
	Long: `Creates an application package file structure.

The application package is copied from a sample application in https://github.com/vespa-engine/sample-apps`,
	Example:           "$ vespa clone vespa-cloud/album-recommendation my-app",
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		if listApps {
			apps, err := listSampleApps()
			if err != nil {
				printErr(err, "Could not list sample applications")
				return
			}
			for _, app := range apps {
				log.Print(app)
			}
		} else {
			if len(args) != 2 {
				fatalErr(nil, "Expected exactly 2 arguments")
				return
			}
			cloneApplication(args[0], args[1])
		}
	},
}

func cloneApplication(source string, name string) {
	zipFile := getSampleAppsZip()
	if zipFile == nil {
		return
	}
	if existingSampleAppsZip == "" { // Indicates we created a temp file now
		defer os.Remove(zipFile.Name())
	}

	zipReader, zipOpenError := zip.OpenReader(zipFile.Name())
	if zipOpenError != nil {
		printErr(zipOpenError, "Could not open sample apps zip '", color.Cyan(zipFile.Name()), "'")
		return
	}
	defer zipReader.Close()

	found := false
	for _, f := range zipReader.File {
		zipEntryPrefix := "sample-apps-master/" + source + "/"
		if strings.HasPrefix(f.Name, zipEntryPrefix) {
			if !found { // Create destination directory lazily when source is found
				createErr := os.Mkdir(name, 0755)
				if createErr != nil {
					printErr(createErr, "Could not create directory '", color.Cyan(name), "'")
					return
				}
			}
			found = true

			copyError := copy(f, name, zipEntryPrefix)
			if copyError != nil {
				printErr(copyError, "Could not copy zip entry '", color.Cyan(f.Name), "' to ", color.Cyan(name))
				return
			}
		}
	}
	if !found {
		printErr(nil, "Could not find source application '", color.Cyan(source), "'")
	} else {
		log.Print("Created ", color.Cyan(name))
	}
}

func getSampleAppsZip() *os.File {
	if existingSampleAppsZip != "" {
		existing, openExistingError := os.Open(existingSampleAppsZip)
		if openExistingError != nil {
			printErr(openExistingError, "Could not open existing sample apps zip file '", color.Cyan(existingSampleAppsZip), "'")
		}
		return existing
	}

	// TODO: Cache it?
	log.Print(color.Yellow("Downloading sample apps ...")) // TODO: Spawn thread to indicate progress
	zipUrl, _ := url.Parse("https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip")
	request := &http.Request{
		URL:    zipUrl,
		Method: "GET",
	}
	response, reqErr := util.HttpDo(request, time.Minute*60, "GitHub")
	if reqErr != nil {
		printErr(reqErr, "Could not download sample apps from GitHub")
		return nil
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		printErr(nil, "Could not download sample apps from GitHub: ", response.StatusCode)
		return nil
	}

	destination, tempFileError := ioutil.TempFile("", "prefix")
	if tempFileError != nil {
		printErr(tempFileError, "Could not create a temporary file to hold sample apps")
	}
	// destination, _ := os.Create("./" + name + "/sample-apps.zip")
	// defer destination.Close()
	_, err := io.Copy(destination, response.Body)
	if err != nil {
		printErr(err, "Could not download sample apps from GitHub")
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
