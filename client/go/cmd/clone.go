// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa clone command
// author: bratseth

package cmd

import (
	"archive/zip"
	"errors"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
)

const sampleAppsCacheTTL = time.Hour * 168 // 1 week

var listApps bool
var forceClone bool

func init() {
	rootCmd.AddCommand(cloneCmd)
	cloneCmd.Flags().BoolVarP(&listApps, "list", "l", false, "List available sample applications")
	cloneCmd.Flags().BoolVarP(&forceClone, "force", "f", false, "Ignore cache and force downloading the latest sample application from GitHub")
}

var cloneCmd = &cobra.Command{
	Use:   "clone sample-application-path target-directory",
	Short: "Create files and directory structure for a new Vespa application from a sample application",
	Long: `Create files and directory structure for a new Vespa application
from a sample application.

Sample applications are downloaded from
https://github.com/vespa-engine/sample-apps.

By default sample applications are cached in the user's cache directory. This
directory can be overriden by setting the VESPA_CLI_CACHE_DIR environment
variable.`,
	Example:           "$ vespa clone vespa-cloud/album-recommendation my-app",
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		if listApps {
			apps, err := listSampleApps()
			if err != nil {
				fatalErr(err, "Could not list sample applications")
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
	defer zipFile.Close()

	zipReader, zipOpenError := zip.OpenReader(zipFile.Name())
	if zipOpenError != nil {
		fatalErr(zipOpenError, "Could not open sample apps zip '", color.Cyan(zipFile.Name()), "'")
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
					fatalErr(createErr, "Could not create directory '", color.Cyan(name), "'")
					return
				}
			}
			found = true

			copyError := copy(f, name, zipEntryPrefix)
			if copyError != nil {
				fatalErr(copyError, "Could not copy zip entry '", color.Cyan(f.Name), "' to ", color.Cyan(name))
				return
			}
		}
	}
	if !found {
		fatalErr(nil, "Could not find source application '", color.Cyan(source), "'")
	} else {
		log.Print("Created ", color.Cyan(name))
	}
}

func openOutputFile() (*os.File, error) {
	cacheDir, err := vespaCliCacheDir()
	if err != nil {
		return nil, err
	}
	cacheFile := filepath.Join(cacheDir, "sample-apps-master.zip")
	return os.OpenFile(cacheFile, os.O_RDWR|os.O_CREATE, 0755)
}

func useCache(cacheFile *os.File) (bool, error) {
	if forceClone {
		return false, nil
	}
	stat, err := cacheFile.Stat()
	if errors.Is(err, os.ErrNotExist) {
		return false, nil
	} else if err != nil {
		return false, err
	}
	expiry := stat.ModTime().Add(sampleAppsCacheTTL)
	return stat.Size() > 0 && time.Now().Before(expiry), nil
}

func getSampleAppsZip() *os.File {
	f, err := openOutputFile()
	if err != nil {
		fatalErr(err, "Could not determine location of cache file")
		return nil
	}
	useCache, err := useCache(f)
	if err != nil {
		fatalErr(err, "Could not determine cache status", "Try ignoring the cache with the -f flag")
		return nil
	}
	if useCache {
		log.Print(color.Yellow("Using cached sample apps ..."))
		return f
	}

	log.Print(color.Yellow("Downloading sample apps ...")) // TODO: Spawn thread to indicate progress
	request, err := http.NewRequest("GET", "https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip", nil)
	if err != nil {
		fatalErr(err, "Invalid URL")
		return nil
	}
	response, err := util.HttpDo(request, time.Minute*60, "GitHub")
	if err != nil {
		fatalErr(err, "Could not download sample apps from GitHub")
		return nil
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		fatalErr(nil, "Could not download sample apps from GitHub: ", response.StatusCode)
		return nil
	}

	if _, err := io.Copy(f, response.Body); err != nil {
		fatalErr(err, "Could not write sample apps to file: ", f.Name())
		return nil
	}
	return f
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
