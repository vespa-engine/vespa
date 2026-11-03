// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"archive/zip"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/fatih/color"
)

// zipFile describes a cached GitHub archive file.
type zipFile struct {
	path    string
	etag    string
	modTime time.Time
}

// githubZipCache downloads a GitHub repository archive (zip) and caches it in the CLI cache directory, keyed by
// entity tag, so that repeated downloads can be skipped via a conditional request.
type githubZipCache struct {
	cli        *CLI
	noCache    bool
	namePrefix string           // prefix of the cached zip file name, e.g. "sample-apps-master"
	tempPrefix string           // prefix of the temporary file used while downloading
	url        string           // URL of the archive to download
	timeout    time.Duration    // HTTP request timeout
	itemName   string           // human-readable name used in progress/error messages, e.g. "sample apps"
	onCacheHit func(msg string) // prints the "Using cached ..." message
}

// zipPath returns the path to the latest cached (or freshly downloaded) archive.
func (g *githubZipCache) zipPath() (string, error) {
	zipFiles, err := g.listZipFiles()
	if err != nil {
		return "", err
	}
	cacheCandidates := zipFiles
	if g.noCache {
		cacheCandidates = nil
	}
	path, cacheHit, err := g.downloadZip(cacheCandidates)
	if err != nil {
		if cacheHit {
			g.cli.printWarning(err)
		} else {
			return "", err
		}
	}
	if cacheHit {
		g.onCacheHit(color.YellowString(fmt.Sprintf("Using cached %s ...", g.itemName)))
	}
	// Remove obsolete files
	for _, zf := range zipFiles {
		if zf.path != path {
			os.Remove(zf.path)
		}
	}
	return path, nil
}

// listZipFiles lists all cached archive files matching namePrefix found in the CLI cache directory.
func (g *githubZipCache) listZipFiles() ([]zipFile, error) {
	dirEntries, err := os.ReadDir(g.cli.config.cacheDir)
	if err != nil {
		return nil, err
	}
	var zipFiles []zipFile
	for _, entry := range dirEntries {
		if filepath.Ext(entry.Name()) != ".zip" {
			continue
		}
		if !strings.HasPrefix(entry.Name(), g.namePrefix) {
			continue
		}
		fi, err := entry.Info()
		if err != nil {
			return nil, err
		}
		zipFiles = append(zipFiles, zipFile{
			path:    filepath.Join(g.cli.config.cacheDir, fi.Name()),
			etag:    parseZipEtag(fi.Name()),
			modTime: fi.ModTime(),
		})
	}
	return zipFiles, nil
}

// parseZipEtag extracts the entity tag embedded in a cached zip file name, e.g. "sample-apps-master_abc123.zip"
// yields "abc123". Returns "" if name has no embedded entity tag.
func parseZipEtag(name string) string {
	name = strings.TrimSuffix(name, ".zip")
	_, etag, found := strings.Cut(name, "_")
	if !found {
		return ""
	}
	return etag
}

// downloadZip downloads the latest archive. If any of cachedFiles is usable, downloading is skipped.
func (g *githubZipCache) downloadZip(cachedFiles []zipFile) (string, bool, error) {
	path := ""
	etag := ""
	sort.Slice(cachedFiles, func(i, j int) bool { return cachedFiles[i].modTime.Before(cachedFiles[j].modTime) })
	if len(cachedFiles) > 0 {
		latest := cachedFiles[len(cachedFiles)-1]
		path = latest.path
		etag = latest.etag
	}
	// The latest cached file, if any, is considered a hit until we have downloaded a fresh one. This allows us to
	// use the cached copy if GitHub is unavailable.
	cacheHit := path != ""
	err := g.cli.spinner(g.cli.Stderr, color.YellowString(fmt.Sprintf("Downloading %s ...", g.itemName)), func() error {
		request, err := http.NewRequest("GET", g.url, nil)
		if err != nil {
			return fmt.Errorf("invalid url: %w", err)
		}
		if etag != "" {
			request.Header = make(http.Header)
			request.Header.Set("if-none-match", fmt.Sprintf(`W/"%s"`, etag))
		}
		response, err := g.cli.httpClient.Do(request, g.timeout)
		if err != nil {
			return fmt.Errorf("could not download %s: %w", g.itemName, err)
		}
		defer response.Body.Close()
		if response.StatusCode == http.StatusNotModified { // entity tag matched so our cached copy is current
			return nil
		}
		if response.StatusCode != http.StatusOK {
			return fmt.Errorf("could not download %s: github returned status %d", g.itemName, response.StatusCode)
		}
		etag = trimEntityTagID(response.Header.Get("etag"))
		newPath, err := g.writeZip(response.Body, etag)
		if err != nil {
			return err
		}
		path = newPath
		cacheHit = false
		return nil
	})
	return path, cacheHit, err
}

// writeZip atomically writes the contents of zipReader to a file in the CLI cache directory.
func (g *githubZipCache) writeZip(zipReader io.Reader, etag string) (string, error) {
	f, err := os.CreateTemp(g.cli.config.cacheDir, g.tempPrefix)
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
		return "", fmt.Errorf("could not write %s to file: %s: %w", g.itemName, f.Name(), err)
	}
	f.Close()
	path := filepath.Join(g.cli.config.cacheDir, g.namePrefix)
	if etag != "" {
		path += "_" + etag
	}
	path += ".zip"
	if err := os.Rename(f.Name(), path); err != nil {
		return "", fmt.Errorf("could not move %s to %s", g.itemName, path)
	}
	cleanTemp = false
	return path, nil
}

func trimEntityTagID(s string) string {
	return strings.TrimSuffix(strings.TrimPrefix(s, `W/"`), `"`)
}

func copyZipEntry(f *zip.File, destinationDir, zipEntryPrefix string) error {
	destinationPath := filepath.Join(destinationDir, filepath.FromSlash(strings.TrimPrefix(f.Name, zipEntryPrefix)))
	if strings.HasSuffix(f.Name, "/") {
		return os.MkdirAll(destinationPath, 0o755)
	}
	if err := os.MkdirAll(filepath.Dir(destinationPath), 0o755); err != nil {
		return err
	}
	r, err := f.Open()
	if err != nil {
		return err
	}
	defer r.Close()
	destination, err := os.Create(destinationPath)
	if err != nil {
		return err
	}
	defer destination.Close()
	if _, err := io.Copy(destination, r); err != nil {
		return err
	}
	return os.Chmod(destinationPath, f.Mode())
}
