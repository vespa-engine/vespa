// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa clone command
// author: bratseth

package cmd

import (
	"archive/zip"
	"errors"
	"fmt"
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
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if listApps {
			apps, err := listSampleApps()
			if err != nil {
				return fmt.Errorf("could not list sample applications: %w", err)
			}
			for _, app := range apps {
				log.Print(app)
			}
			return nil
		}
		if len(args) != 2 {
			return fmt.Errorf("expected exactly 2 arguments, got %d", len(args))
		}
		return cloneApplication(args[0], args[1])
	},
}

func cloneApplication(applicationName string, applicationDir string) error {
	zipFile, err := getSampleAppsZip()
	if err != nil {
		return err
	}
	defer zipFile.Close()

	r, err := zip.OpenReader(zipFile.Name())
	if err != nil {
		return fmt.Errorf("could not open sample apps zip '%s': %w", color.Cyan(zipFile.Name()), err)
	}
	defer r.Close()

	found := false
	for _, f := range r.File {
		dirPrefix := "sample-apps-master/" + applicationName + "/"
		if strings.HasPrefix(f.Name, dirPrefix) {
			if !found { // Create destination directory lazily when source is found
				createErr := os.Mkdir(applicationDir, 0755)
				if createErr != nil {
					return fmt.Errorf("could not create directory '%s': %w", color.Cyan(applicationDir), createErr)
				}
			}
			found = true

			if err := copy(f, applicationDir, dirPrefix); err != nil {
				return fmt.Errorf("could not copy zip entry '%s': %w", color.Cyan(f.Name), err)
			}
		}
	}
	if !found {
		return errHint(fmt.Errorf("could not find source application '%s'", color.Cyan(applicationName)), "Use -f to ignore the cache")
	} else {
		log.Print("Created ", color.Cyan(applicationDir))
	}
	return nil
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

func getSampleAppsZip() (*os.File, error) {
	f, err := openOutputFile()
	if err != nil {
		return nil, fmt.Errorf("could not determine location of cache file: %w", err)
	}
	useCache, err := useCache(f)
	if err != nil {
		return nil, errHint(fmt.Errorf("could not determine cache status: %w", err), "Try ignoring the cache with the -f flag")
	}
	if useCache {
		log.Print(color.Yellow("Using cached sample apps ..."))
		return f, nil
	}
	err = util.Spinner(color.Yellow("Downloading sample apps ...").String(), func() error {
		request, err := http.NewRequest("GET", "https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip", nil)
		if err != nil {
			return fmt.Errorf("invalid url: %w", err)
		}
		response, err := util.HttpDo(request, time.Minute*60, "GitHub")
		if err != nil {
			return fmt.Errorf("could not download sample apps: %w", err)
		}
		defer response.Body.Close()
		if response.StatusCode != 200 {
			return fmt.Errorf("could not download sample apps: github returned status %d", response.StatusCode)
		}
		if err := f.Truncate(0); err != nil {
			return fmt.Errorf("could not truncate sample apps file: %s: %w", f.Name(), err)
		}
		if _, err := io.Copy(f, response.Body); err != nil {
			return fmt.Errorf("could not write sample apps to file: %s: %w", f.Name(), err)
		}
		return nil
	})
	return f, err
}

func copy(f *zip.File, destinationDir string, zipEntryPrefix string) error {
	destinationPath := filepath.Join(destinationDir, filepath.FromSlash(strings.TrimPrefix(f.Name, zipEntryPrefix)))
	if strings.HasSuffix(f.Name, "/") {
		if f.Name != zipEntryPrefix { // root is already created
			if err := os.Mkdir(destinationPath, 0755); err != nil {
				return err
			}
		}
	} else {
		r, err := f.Open()
		if err != nil {
			return err
		}
		defer r.Close()
		destination, err := os.Create(destinationPath)
		if err != nil {
			return err
		}
		if _, err := io.Copy(destination, r); err != nil {
			return err
		}
		if err := os.Chmod(destinationPath, f.Mode()); err != nil {
			return err
		}
	}
	return nil
}
