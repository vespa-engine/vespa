// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/cli/build"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

func newVersionCmd(cli *CLI) *cobra.Command {
	var skipVersionCheck bool
	cmd := &cobra.Command{
		Use:               "version",
		Short:             "Show current version and check for updates",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			log.Printf("Vespa CLI version %s compiled with %v on %v/%v", build.Version, runtime.Version(), runtime.GOOS, runtime.GOARCH)
			if !skipVersionCheck && cli.isTerminal() {
				return checkVersion(cli)
			}
			return nil
		},
	}
	cmd.Flags().BoolVarP(&skipVersionCheck, "no-check", "n", false, "Do not check if a new version is available")
	return cmd
}

func checkVersion(cli *CLI) error {
	current, err := version.Parse(build.Version)
	if err != nil {
		return err
	}
	latest, err := latestRelease(cli)
	if err != nil {
		return err
	}
	if !current.Less(latest.Version) {
		return nil
	}
	usingHomebrew := usingHomebrew(cli)
	if usingHomebrew && latest.isRecent() {
		return nil // Allow some time for new release to appear in Homebrew repo
	}
	log.Printf("\nNew release available: %s", color.GreenString(latest.Version.String()))
	log.Printf("https://github.com/vespa-engine/vespa/releases/tag/v%s", latest.Version)
	if usingHomebrew {
		log.Printf("\nUpgrade by running:\n%s", color.CyanString("brew update && brew upgrade vespa-cli"))
	}
	return nil
}

func latestRelease(cli *CLI) (release, error) {
	req, err := http.NewRequest("GET", "https://api.github.com/repos/vespa-engine/vespa/releases", nil)
	if err != nil {
		return release{}, err
	}
	response, err := cli.httpClient.Do(req, time.Minute)
	if err != nil {
		return release{}, err
	}
	defer response.Body.Close()

	var ghReleases []githubRelease
	dec := json.NewDecoder(response.Body)
	if err := dec.Decode(&ghReleases); err != nil {
		return release{}, err
	}
	if len(ghReleases) == 0 {
		return release{}, nil // No releases found
	}

	var releases []release
	for _, r := range ghReleases {
		v, err := version.Parse(r.TagName)
		if err != nil {
			return release{}, err
		}
		publishedAt, err := time.Parse(time.RFC3339, r.PublishedAt)
		if err != nil {
			return release{}, err
		}
		releases = append(releases, release{Version: v, PublishedAt: publishedAt})
	}
	sort.Slice(releases, func(i, j int) bool { return releases[i].Version.Less(releases[j].Version) })
	return releases[len(releases)-1], nil
}

func usingHomebrew(cli *CLI) bool {
	selfPath, err := cli.exec.LookPath("vespa")
	if err != nil {
		return false
	}
	brewPrefix, err := cli.exec.Run("brew", "--prefix")
	if err != nil {
		return false
	}
	brewBin := filepath.Join(strings.TrimSpace(string(brewPrefix)), "bin") + string(os.PathSeparator)
	return strings.HasPrefix(selfPath, brewBin)
}

type githubRelease struct {
	TagName     string `json:"tag_name"`
	PublishedAt string `json:"published_at"`
}

type release struct {
	Version     version.Version
	PublishedAt time.Time
}

func (r release) isRecent() bool {
	return time.Now().Before(r.PublishedAt.Add(time.Hour * 24))
}
