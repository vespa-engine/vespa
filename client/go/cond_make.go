// This is a wrapper around make that runs the given target conditionally, i.e. only when considered necessary.
//
// For example, the Homebrew target only bumps the formula for vespa-cli if no pull request has previously been made
// for the latest release.
//
// This source file is not part of the standard Vespa CLI build and is only used from the Makefile in this directory.

//go:build ignore
// +build ignore

package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"strings"
)

func init() {
	log.SetPrefix("cond-make: ")
	log.SetFlags(0) // No timestamps
}

func requireEnv(variable string) (string, error) {
	value := os.Getenv(variable)
	if value == "" {
		return "", fmt.Errorf("environment variable %s is not set", variable)
	}
	return value, nil
}

func quote(args []string) string {
	var sb strings.Builder
	for i, arg := range args {
		if strings.Contains(arg, " ") {
			sb.WriteString(fmt.Sprintf("%q", arg))
		} else {
			sb.WriteString(arg)
		}
		if i < len(args)-1 {
			sb.WriteString(" ")
		}
	}
	return sb.String()
}

func newCmd(name string, arg ...string) (*exec.Cmd, *bytes.Buffer, *bytes.Buffer) {
	cmd := exec.Command(name, arg...)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = io.MultiWriter(os.Stdout, &stdout)
	cmd.Stderr = io.MultiWriter(os.Stderr, &stderr)
	log.Printf("$ %s", quote(cmd.Args))
	return cmd, &stdout, &stderr
}

func runCmd(name string, arg ...string) (string, string, error) {
	cmd, stdout, stderr := newCmd(name, arg...)
	err := cmd.Run()
	return stdout.String(), stderr.String(), err
}

// latestTag returns the most recent tag as determined by sorting local git tags as version numbers.
func latestTag() (string, error) {
	stdout, _, err := runCmd("sh", "-c", "git tag -l 'v[0-9]*' | sort -V | tail -1")
	if err != nil {
		return "", err
	}
	version := strings.TrimSpace(stdout)
	if version == "" {
		return "", fmt.Errorf("no tag found")
	}
	return version, nil
}

// latestReleasedTag returns the tag of the most recent release available on given mirror.
func latestReleasedTag(mirror string) (string, error) {
	switch mirror {
	case "github":
		url := "https://api.github.com/repos/vespa-engine/vespa/releases/latest"
		resp, err := http.Get(url)
		if err != nil {
			return "", err
		}
		defer resp.Body.Close()
		if resp.StatusCode != http.StatusOK {
			return "", fmt.Errorf("got status %d from %s", resp.StatusCode, url)
		}
		var release gitHubRelease
		dec := json.NewDecoder(resp.Body)
		if err := dec.Decode(&release); err != nil {
			return "", err
		}
		return release.TagName, nil
	case "homebrew":
		cmd, stdout, _ := newCmd("brew", "info", "--json", "--formula", "vespa-cli")
		cmd.Stdout = stdout // skip printing output to os.Stdout
		if err := cmd.Run(); err != nil {
			return "", err
		}
		var brewInfo []brewFormula
		if err := json.Unmarshal(stdout.Bytes(), &brewInfo); err != nil {
			return "", err
		}
		if len(brewInfo) == 0 {
			return "", fmt.Errorf("vespa-cli formula not found")
		}
		return "v" + brewInfo[0].Versions.Stable, nil
	}
	return "", fmt.Errorf("invalid mirror: %q", mirror)
}

// hasChanges returns true if there are changes to Vespa CLI code between tag1 and tag2.
func hasChanges(tag1, tag2 string) (bool, error) {
	_, _, err := runCmd("git", "diff", "--quiet", tag1, tag2, ".")
	if err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			switch exitErr.ExitCode() {
			case 0:
				return false, nil
			case 1:
				return true, nil
			}
		}
	}
	return false, err
}

// candidateTag returns the latest tag that should be released to mirror. If there is nothing to release, the returned
// tag is empty.
func candidateTag(mirror string) (string, error) {
	latestTag, err := latestTag()
	if err != nil {
		return "", err
	}
	releasedTag, err := latestReleasedTag(mirror)
	if err != nil {
		return "", err
	}
	changes, err := hasChanges(releasedTag, latestTag)
	if err != nil {
		return "", err
	}
	if !changes {
		log.Printf("no changes found between %s and %s: skipping release", releasedTag, latestTag)
		return "", nil
	}
	log.Printf("found changes between %s and %s: creating release", releasedTag, latestTag)
	return latestTag, nil
}

// switchToTag checks out the given tag in git and returns the current branch name. The Makefile and this file always
// preserved from current branch after checking out tag.
func switchToTag(tag string) (string, error) {
	stdout, _, err := runCmd("git", "rev-parse", "--abbrev-ref", "HEAD")
	if err != nil {
		return "", err
	}
	prevBranch := strings.TrimSpace(stdout)
	if err := checkoutRef(tag); err != nil {
		return "", err
	}
	_, _, err = runCmd("git", "checkout", prevBranch, "Makefile", "cond_make.go")
	if err != nil {
		return "", err
	}
	return prevBranch, err
}

func checkoutRef(ref string) error {
	_, _, err := runCmd("git", "checkout", ref)
	return err
}

// releaseToHomebrew releases Vespa CLI to GitHub by calling the given make target, if necessary.
func releaseToHomebrew(target string) error {
	if _, err := requireEnv("HOMEBREW_GITHUB_API_TOKEN"); err != nil {
		return err
	}
	tag, err := candidateTag("homebrew")
	if tag == "" || err != nil {
		return err
	}
	prevBranch, err := switchToTag(tag)
	if err != nil {
		return err
	}
	defer checkoutRef(prevBranch)
	_, stderr, err := runCmd("make", "--", target)
	if err != nil {
		if strings.Contains(stderr, "Error: These pull requests may be duplicates:") {
			return nil // fine, pull request already created
		}
	}
	return err
}

// releaseToGitHub releases Vespa CLI to GitHub by calling the given make target, if necessary.
func releaseToGitHub(target string) error {
	if _, err := requireEnv("GH_TOKEN"); err != nil {
		return err
	}
	tag, err := candidateTag("github")
	if tag == "" || err != nil {
		return err
	}
	prevBranch, err := switchToTag(tag)
	if err != nil {
		return err
	}
	defer checkoutRef(prevBranch)
	_, _, err = runCmd("make", "--", target)
	return err
}

func main() {
	if len(os.Args) != 2 {
		log.Fatalf("usage: %s TARGET", os.Args[0])
	}
	target := os.Args[1]
	switch target {
	case "--dist-homebrew":
		if err := releaseToHomebrew(target); err != nil {
			log.Fatal(err)
		}
	case "--dist-github":
		if err := releaseToGitHub(target); err != nil {
			log.Fatal(err)
		}
	default:
		log.Fatalf("unsupported target: %s", target)
	}
}

type gitHubRelease struct {
	TagName string `json:"tag_name"`
}

type brewFormula struct {
	Versions brewVersions `json:"versions"`
}

type brewVersions struct {
	Stable string `json:"stable"`
}
