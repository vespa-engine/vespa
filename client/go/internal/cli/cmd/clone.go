// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa clone command
// author: bratseth

package cmd

import (
	"archive/zip"
	"errors"
	"fmt"
	"io/fs"
	"log"
	"os"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
)

const sampleAppsNamePrefix = "sample-apps-master"

func newCloneCmd(cli *CLI) *cobra.Command {
	var (
		listApps bool
		noCache  bool
	)
	cmd := &cobra.Command{
		Use:   "clone sample-application-path target-directory",
		Short: "Create files and directory structure from a Vespa sample application",
		Long: `Create files and directory structure from a Vespa sample application.

Sample applications are downloaded from
https://github.com/vespa-engine/sample-apps.

By default, sample applications are cached in the user's cache directory. This
directory can be overridden by setting the VESPA_CLI_CACHE_DIR environment
variable.`,
		Example:           "$ vespa clone album-recommendation my-app",
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
			cloner := &cloner{cli: cli, noCache: noCache}
			return cloner.Clone(args[0], args[1])
		},
	}
	cmd.Flags().BoolVarP(&listApps, "list", "l", false, "List available sample applications")
	cmd.Flags().BoolVarP(&noCache, "force", "f", false, "Ignore cache and force downloading the latest sample application from GitHub")
	return cmd
}

type cloner struct {
	cli     *CLI
	noCache bool
}

// zipCache returns the githubZipCache used to download and cache the sample apps archive.
func (c *cloner) zipCache() *githubZipCache {
	return &githubZipCache{
		cli:        c.cli,
		noCache:    c.noCache,
		namePrefix: sampleAppsNamePrefix,
		tempPrefix: "sample-apps-tmp-",
		url:        "https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip",
		timeout:    time.Minute * 60,
		itemName:   "sample apps",
		onCacheHit: func(msg string) { log.Print(msg) },
	}
}

func (c *cloner) createDirectory(path string) error {
	if err := os.Mkdir(path, 0o755); err != nil {
		if errors.Is(err, fs.ErrExist) {
			entries, err := os.ReadDir(path)
			if err != nil {
				return err
			}
			if len(entries) > 0 {
				return fmt.Errorf("%s already exists and is not empty", path)
			}
		} else {
			return err
		}
	}
	return nil
}

// Clone copies the application identified by applicationName into given path. If the cached copy of sample applications
// has expired (as determined by its entity tag), a current copy will be downloaded from GitHub automatically.
func (c *cloner) Clone(applicationName, path string) error {
	zipPath, err := c.zipPath()
	if err != nil {
		return err
	}

	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return fmt.Errorf("could not open sample apps zip '%s': %w", color.CyanString(zipPath), err)
	}
	defer r.Close()

	found := false
	for _, f := range r.File {
		dirPrefix := "sample-apps-master/" + applicationName + "/"
		if strings.HasPrefix(f.Name, dirPrefix) {
			if !found { // Create destination directory lazily when source is found
				if err := c.createDirectory(path); err != nil {
					return fmt.Errorf("could not create directory: %w", err)
				}
			}
			found = true

			if err := copyZipEntry(f, path, dirPrefix); err != nil {
				return fmt.Errorf("could not copy zip entry '%s': %w", color.CyanString(f.Name), err)
			}
		}
	}

	if !found {
		return errHint(fmt.Errorf("could not find source application '%s'", color.CyanString(applicationName)), "Use -f to ignore the cache")
	} else {
		log.Print("Cloned into ", color.CyanString(path))
	}
	return nil
}

// zipPath returns the path to the latest sample application ZIP file.
func (c *cloner) zipPath() (string, error) {
	return c.zipCache().zipPath()
}
