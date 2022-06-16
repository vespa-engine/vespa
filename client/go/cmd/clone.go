// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa clone command
// author: bratseth

package cmd

import (
	"archive/zip"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sort"
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
		Short: "Create files and directory structure for a new Vespa application from a sample application",
		Long: `Create files and directory structure for a new Vespa application
from a sample application.

Sample applications are downloaded from
https://github.com/vespa-engine/sample-apps.

By default sample applications are cached in the user's cache directory. This
directory can be overriden by setting the VESPA_CLI_CACHE_DIR environment
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

type zipFile struct {
	path    string
	etag    string
	modTime time.Time
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

// zipPath returns the path to the latest sample application ZIP file.
func (c *cloner) zipPath() (string, error) {
	zipFiles, err := c.listZipFiles()
	if err != nil {
		return "", nil
	}
	cacheCandidates := zipFiles
	if c.noCache {
		cacheCandidates = nil
	}
	zipPath, cacheHit, err := c.downloadZip(cacheCandidates)
	if err != nil {
		if cacheHit {
			c.cli.printWarning(err)
		} else {
			return "", err
		}
	}
	if cacheHit {
		log.Print(color.YellowString("Using cached sample apps ..."))
	}
	// Remove obsolete files
	for _, zf := range zipFiles {
		if zf.path != zipPath {
			os.Remove(zf.path)
		}
	}
	return zipPath, nil
}

// listZipFiles list all sample apps ZIP files found in cacheDir.
func (c *cloner) listZipFiles() ([]zipFile, error) {
	dirEntries, err := os.ReadDir(c.cli.config.cacheDir)
	if err != nil {
		return nil, err
	}
	var zipFiles []zipFile
	for _, entry := range dirEntries {
		ext := filepath.Ext(entry.Name())
		if ext != ".zip" {
			continue
		}
		if !strings.HasPrefix(entry.Name(), sampleAppsNamePrefix) {
			continue
		}
		fi, err := entry.Info()
		if err != nil {
			return nil, err
		}
		name := fi.Name()
		etag := ""
		parts := strings.Split(name, "_")
		if len(parts) == 2 {
			etag = strings.TrimSuffix(parts[1], ext)
		}
		zipFiles = append(zipFiles, zipFile{
			path:    filepath.Join(c.cli.config.cacheDir, name),
			etag:    etag,
			modTime: fi.ModTime(),
		})
	}
	return zipFiles, nil
}

// downloadZip conditionally downloads the latest sample apps ZIP file. If any of the ZIP files among cacheFiles are
// usable, downloading is skipped.
func (c *cloner) downloadZip(cachedFiles []zipFile) (string, bool, error) {
	zipPath := ""
	etag := ""
	sort.Slice(cachedFiles, func(i, j int) bool { return cachedFiles[i].modTime.Before(cachedFiles[j].modTime) })
	if len(cachedFiles) > 0 {
		latest := cachedFiles[len(cachedFiles)-1]
		zipPath = latest.path
		etag = latest.etag
	}
	// The latest cached file, if any, is considered a hit until we have downloaded a fresh one. This allows us to use
	// the cached copy if GitHub is unavailable.
	cacheHit := zipPath != ""
	err := c.cli.spinner(c.cli.Stderr, color.YellowString("Downloading sample apps ..."), func() error {
		request, err := http.NewRequest("GET", "https://github.com/vespa-engine/sample-apps/archive/refs/heads/master.zip", nil)
		if err != nil {
			return fmt.Errorf("invalid url: %w", err)
		}
		if etag != "" {
			request.Header = make(http.Header)
			request.Header.Set("if-none-match", fmt.Sprintf(`W/"%s"`, etag))
		}
		response, err := c.cli.httpClient.Do(request, time.Minute*60)
		if err != nil {
			return fmt.Errorf("could not download sample apps: %w", err)
		}
		defer response.Body.Close()
		if response.StatusCode == http.StatusNotModified { // entity tag matched so our cached copy is current
			return nil
		}
		if response.StatusCode != http.StatusOK {
			return fmt.Errorf("could not download sample apps: github returned status %d", response.StatusCode)
		}
		etag = trimEntityTagID(response.Header.Get("etag"))
		newPath, err := c.writeZip(response.Body, etag)
		if err != nil {
			return err
		}
		zipPath = newPath
		cacheHit = false
		return nil
	})
	return zipPath, cacheHit, err
}

// writeZip atomically writes the contents of reader zipReader to a file in the CLI cache directory.
func (c *cloner) writeZip(zipReader io.Reader, etag string) (string, error) {
	f, err := os.CreateTemp(c.cli.config.cacheDir, "sample-apps-tmp-")
	if err != nil {
		return "", fmt.Errorf("could not create temporary file: %w", err)
	}
	cleanTemp := true
	defer func() {
		f.Close()
		if cleanTemp {
			os.Remove(f.Name())
		}
	}()
	if _, err := io.Copy(f, zipReader); err != nil {
		return "", fmt.Errorf("could not write sample apps to file: %s: %w", f.Name(), err)
	}
	f.Close()
	path := filepath.Join(c.cli.config.cacheDir, sampleAppsNamePrefix)
	if etag != "" {
		path += "_" + etag
	}
	path += ".zip"
	if err := os.Rename(f.Name(), path); err != nil {
		return "", fmt.Errorf("could not move sample apps to %s", path)
	}
	cleanTemp = false
	return path, nil
}

func trimEntityTagID(s string) string {
	return strings.TrimSuffix(strings.TrimPrefix(s, `W/"`), `"`)
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
