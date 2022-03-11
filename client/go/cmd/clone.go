// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa clone command
// author: bratseth

package cmd

import (
	"archive/zip"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
)

func newCloneCmd(cli *CLI) *cobra.Command {
	var (
		listApps   bool
		forceClone bool
	)
	cmd := &cobra.Command{
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
				apps, err := listSampleApps(cli.httpClient)
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
			cloner := &cloner{cli: cli, discardCache: forceClone}
			return cloner.Clone(args[0], args[1])
		},
	}
	cmd.Flags().BoolVarP(&listApps, "list", "l", false, "List available sample applications")
	cmd.Flags().BoolVarP(&forceClone, "force", "f", false, "Ignore cache and force downloading the latest sample application from GitHub")
	return cmd
}

type cloner struct {
	cli          *CLI
	discardCache bool
}

// Clone copies the application identified by applicationName into given path. If the cached copy of sample applications
// has expired, it will be downloaded from GitHub automatically.
func (c *cloner) Clone(applicationName, path string) error {
	zipName, err := c.zipName()
	if err != nil {
		return err
	}

	r, err := zip.OpenReader(zipName)
	if err != nil {
		return fmt.Errorf("could not open sample apps zip '%s': %w", color.CyanString(zipName), err)
	}
	defer r.Close()

	found := false
	for _, f := range r.File {
		dirPrefix := "sample-apps-master/" + applicationName + "/"
		if strings.HasPrefix(f.Name, dirPrefix) {
			if !found { // Create destination directory lazily when source is found
				createErr := os.Mkdir(path, 0755)
				if createErr != nil {
					return fmt.Errorf("could not create directory '%s': %w", color.CyanString(path), createErr)
				}
			}
			found = true

			if err := copy(f, path, dirPrefix); err != nil {
				return fmt.Errorf("could not copy zip entry '%s': %w", color.CyanString(f.Name), err)
			}
		}
	}
	if !found {
		return errHint(fmt.Errorf("could not find source application '%s'", color.CyanString(applicationName)), "Use -f to ignore the cache")
	} else {
		log.Print("Created ", color.CyanString(path))
	}
	return nil
}

func (c *cloner) useCache(stat os.FileInfo) (bool, error) {
	if c.discardCache {
		return false, nil
	}
	expiry := stat.ModTime().Add(time.Hour * 168) // 1 week
	return stat.Size() > 0 && time.Now().Before(expiry), nil
}

func (c *cloner) downloadZip(dst string) error {
	f, err := ioutil.TempFile(filepath.Dir(dst), "sample-apps")
	if err != nil {
		return fmt.Errorf("could not create temporary file: %w", err)
	}
	defer f.Close()
	return c.cli.spinner(c.cli.Stderr, color.YellowString("Downloading sample apps ..."), func() error {
		request, err := http.NewRequest("GET", "https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip", nil)
		if err != nil {
			return fmt.Errorf("invalid url: %w", err)
		}
		response, err := c.cli.httpClient.Do(request, time.Minute*60)
		if err != nil {
			return fmt.Errorf("could not download sample apps: %w", err)
		}
		defer response.Body.Close()
		if response.StatusCode != http.StatusOK {
			return fmt.Errorf("could not download sample apps: github returned status %d", response.StatusCode)
		}
		if _, err := io.Copy(f, response.Body); err != nil {
			return fmt.Errorf("could not write sample apps to file: %s: %w", f.Name(), err)
		}
		f.Close()
		if err := os.Rename(f.Name(), dst); err != nil {
			return fmt.Errorf("could not move sample apps to cache path")
		}
		return nil
	})
}

func (c *cloner) zipName() (string, error) {
	cacheDir, err := vespaCliCacheDir(c.cli.Environment)
	if err != nil {
		return "", err
	}
	dst := filepath.Join(cacheDir, "sample-apps-master.zip")
	cacheExists := true
	stat, err := os.Stat(dst)
	if errors.Is(err, os.ErrNotExist) {
		cacheExists = false
	} else if err != nil {
		return "", fmt.Errorf("could not stat existing cache file: %w", err)
	}
	if cacheExists {
		useCache, err := c.useCache(stat)
		if err != nil {
			return "", errHint(fmt.Errorf("could not determine cache status: %w", err), "Try ignoring the cache with the -f flag")
		}
		if useCache {
			log.Print(color.YellowString("Using cached sample apps ..."))
			return dst, nil
		}
	}
	if err := c.downloadZip(dst); err != nil {
		return "", fmt.Errorf("could not fetch sample apps: %w", err)
	}
	return dst, nil
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
