// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/build"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/version"
)

var skipVersionCheck bool

var sp subprocess = &execSubprocess{}

type subprocess interface {
	pathOf(name string) (string, error)
	outputOf(name string, args ...string) ([]byte, error)
	isTerminal() bool
}

type execSubprocess struct{}

func (c *execSubprocess) pathOf(name string) (string, error) { return exec.LookPath(name) }
func (c *execSubprocess) isTerminal() bool                   { return isTerminal() }
func (c *execSubprocess) outputOf(name string, args ...string) ([]byte, error) {
	return exec.Command(name, args...).Output()
}

func init() {
	rootCmd.AddCommand(versionCmd)
	versionCmd.Flags().BoolVarP(&skipVersionCheck, "no-check", "n", false, "Do not check if a new version is available")
}

var versionCmd = &cobra.Command{
	Use:               "version",
	Short:             "Show current version and check for updates",
	DisableAutoGenTag: true,
	Args:              cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		log.Printf("vespa version %s compiled with %v on %v/%v", build.Version, runtime.Version(), runtime.GOOS, runtime.GOARCH)
		if !skipVersionCheck && sp.isTerminal() {
			if err := checkVersion(); err != nil {
				fatalErr(err)
			}
		}
	},
}

func checkVersion() error {
	current, err := version.Parse(build.Version)
	if err != nil {
		return err
	}
	latest, err := latestRelease()
	if err != nil {
		return err
	}
	if !current.Less(latest.Version) {
		return nil
	}
	usingHomebrew := usingHomebrew()
	if usingHomebrew && latest.isRecent() {
		return nil // Allow some time for new release to appear in Homebrew repo
	}
	log.Printf("\nNew release available: %s", color.Green(latest.Version))
	log.Printf("https://github.com/vespa-engine/vespa/releases/tag/v%s", latest.Version)
	if usingHomebrew {
		log.Printf("\nUpgrade by running:\n%s", color.Cyan("brew update && brew upgrade vespa-cli"))
	}
	return nil
}

func latestRelease() (release, error) {
	req, err := http.NewRequest("GET", "https://api.github.com/repos/vespa-engine/vespa/releases", nil)
	if err != nil {
		return release{}, err
	}
	response, err := util.HttpDo(req, time.Minute, "GitHub")
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

func usingHomebrew() bool {
	selfPath, err := sp.pathOf("vespa")
	if err != nil {
		return false
	}
	brewPrefix, err := sp.outputOf("brew", "--prefix")
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
