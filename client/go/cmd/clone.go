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
	"github.com/vespa-engine/vespa/util"
)

// Set this to test without downloading this file from github
var existingSampleAppsZip string

func init() {
	existingSampleAppsZip = ""
	rootCmd.AddCommand(cloneCmd)
}

var cloneCmd = &cobra.Command{
	// TODO: "application" and "list" subcommands?
	Use:     "clone",
	Short:   "Create files and directory structure for a new Vespa application from a template",
	Example: "$ vespa clone vespa-cloud/album-recommendation my-app",
	Args:    cobra.ExactArgs(2),
	Run: func(cmd *cobra.Command, args []string) {
		cloneApplication(args[0], args[1])
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
		log.Print(color.Red("Error: "), "Could not open sample apps zip '", color.Cyan(zipFile.Name()), "'")
		log.Print(color.Brown(zipOpenError))
	}
	defer zipReader.Close()

	found := false
	for _, f := range zipReader.File {
		zipEntryPrefix := "sample-apps-master/" + source + "/"
		if strings.HasPrefix(f.Name, zipEntryPrefix) {
			if !found { // Create destination directory lazily when source is found
				createErr := os.Mkdir(name, 0755)
				if createErr != nil {
					log.Print(color.Red("Error: "), "Could not create directory '", color.Cyan(name), "'")
					log.Print(color.Brown(createErr))
					return
				}
			}
			found = true

			copyError := copy(f, name, zipEntryPrefix)
			if copyError != nil {
				log.Print(color.Red("Error: "), "Could not copy zip entry '", color.Cyan(f.Name), "' to ", color.Cyan(name))
				log.Print(color.Brown(copyError))
				return
			}
		}
	}
	if !found {
		log.Print(color.Red("Error: "), "Could not find source application '", color.Cyan(source), "'")
	} else {
		log.Print("Created ", color.Cyan(name))
	}
}

func getSampleAppsZip() *os.File {
	if existingSampleAppsZip != "" {
		existing, openExistingError := os.Open(existingSampleAppsZip)
		if openExistingError != nil {
			log.Print(color.Red("Error: "), "Could not open existing sample apps zip file '", color.Cyan(existingSampleAppsZip), "'")
			log.Print(color.Brown(openExistingError))
		}
		return existing
	}

	// TODO: Cache it?
	log.Print(color.Brown("Downloading sample apps ...")) // TODO: Spawn thread to indicate progress
	zipUrl, _ := url.Parse("https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip")
	request := &http.Request{
		URL:    zipUrl,
		Method: "GET",
	}
	response, reqErr := util.HttpDo(request, time.Minute*60, "GitHub")
	if reqErr != nil {
		log.Print(color.Red("Error: "), "Could not download sample apps from github")
		log.Print(color.Brown(reqErr))
		return nil
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		log.Print(color.Red("Error: "), "Could not download sample apps from github:", response.StatusCode)
		return nil
	}

	destination, tempFileError := ioutil.TempFile("", "prefix")
	if tempFileError != nil {
		log.Print(color.Red("Error: "), "Could not create a temp file to hold sample apps")
		log.Print(color.Brown(tempFileError))
	}
	// destination, _ := os.Create("./" + name + "/sample-apps.zip")
	// defer destination.Close()
	_, err := io.Copy(destination, response.Body)
	if err != nil {
		log.Print(color.Red("Error: "), "Could not download sample apps from GitHub")
		log.Print(color.Brown(err))
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
