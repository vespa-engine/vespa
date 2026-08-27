// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"archive/zip"
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

const skillsZipNamePrefix = "vespa-skills-" + skillsRepoRef

// skillMeta holds the metadata of a skill, parsed from its SKILL.md frontmatter.
type skillMeta struct {
	name        string
	description string
}

// skillsSource fetches and caches the vespa-engine/skills archive from GitHub.
type skillsSource struct {
	cli     *CLI
	noCache bool
}

// zipCache returns the githubZipCache used to download and cache the skills archive.
func (s *skillsSource) zipCache() *githubZipCache {
	return &githubZipCache{
		cli:        s.cli,
		noCache:    s.noCache,
		namePrefix: skillsZipNamePrefix,
		tempPrefix: "vespa-skills-tmp-",
		url:        fmt.Sprintf("https://github.com/%s/%s/archive/refs/heads/%s.zip", skillsRepoOwner, skillsRepoName, skillsRepoRef),
		timeout:    time.Minute * 5,
		itemName:   "skills",
		onCacheHit: func(msg string) { s.cli.printInfo(msg) },
	}
}

// zipPath returns the path to the latest cached (or freshly downloaded) skills archive.
func (s *skillsSource) zipPath() (string, error) {
	return s.zipCache().zipPath()
}

// skillsZipEntryPrefix returns the top-level directory prefix inside the downloaded archive, e.g. "skills-main/".
func skillsZipEntryPrefix() string {
	return fmt.Sprintf("%s-%s/", skillsRepoName, skillsRepoRef)
}

// skillsArchiveRef returns the entity tag embedded in the given skills archive path, if any.
func skillsArchiveRef(zipPath string) string {
	return parseZipEtag(filepath.Base(zipPath))
}

// openSkillsZip opens the skills archive at zipPath. The caller must Close the returned reader.
func openSkillsZip(zipPath string) (*zip.ReadCloser, error) {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return nil, fmt.Errorf("could not open skills archive '%s': %w", zipPath, err)
	}
	return r, nil
}

func listAvailableSkills(r *zip.Reader) ([]skillMeta, error) {
	prefix := skillsZipEntryPrefix()
	var skills []skillMeta
	for _, f := range r.File {
		rel := strings.TrimPrefix(f.Name, prefix)
		if rel == f.Name {
			continue // entry is not inside the repository root
		}
		parts := strings.SplitN(rel, "/", 2)
		if len(parts) != 2 || parts[1] != "SKILL.md" {
			continue
		}
		meta, err := parseSkillFrontmatter(f)
		if err != nil {
			return nil, fmt.Errorf("could not parse %s: %w", f.Name, err)
		}
		if meta.name == "" {
			meta.name = parts[0]
		}
		skills = append(skills, meta)
	}
	sort.Slice(skills, func(i, j int) bool { return skills[i].name < skills[j].name })
	return skills, nil
}

func parseSkillFrontmatter(f *zip.File) (skillMeta, error) {
	rc, err := f.Open()
	if err != nil {
		return skillMeta{}, err
	}
	defer rc.Close()
	var meta skillMeta
	delimiters := 0
	scanner := bufio.NewScanner(rc)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.TrimSpace(line) == "---" {
			delimiters++
			if delimiters == 2 {
				break
			}
			continue
		}
		if delimiters != 1 {
			continue
		}
		key, value, ok := strings.Cut(line, ":")
		if !ok {
			continue
		}
		key = strings.TrimSpace(key)
		value = strings.Trim(strings.TrimSpace(value), `"`)
		switch key {
		case "name":
			meta.name = value
		case "description":
			meta.description = value
		}
	}
	return meta, scanner.Err()
}

func extractSkill(r *zip.Reader, skillName, destDir string) error {
	dirPrefix := skillsZipEntryPrefix() + skillName + "/"
	found := false
	for _, f := range r.File {
		if !strings.HasPrefix(f.Name, dirPrefix) {
			continue
		}
		found = true
		if err := copyZipEntry(f, destDir, dirPrefix); err != nil {
			return fmt.Errorf("could not copy zip entry '%s': %w", f.Name, err)
		}
	}
	if !found {
		return fmt.Errorf("could not find skill '%s' in archive", skillName)
	}
	return nil
}

func extractSkillToHarnesses(r *zip.Reader, skillName string, harnesses []skillHarness, root string, checkConflict, force bool) ([]string, error) {
	var written []string
	seen := make(map[string]bool)
	for _, h := range harnesses {
		dest := filepath.Join(root, h.dir, skillName)
		if seen[dest] {
			continue
		}
		seen[dest] = true
		if checkConflict {
			if err := checkSkillDestConflict(dest, force); err != nil {
				return nil, fmt.Errorf("could not install %s for %s: %w", skillName, h.displayName, err)
			}
		}
		if err := extractSkill(r, skillName, dest); err != nil {
			return nil, err
		}
		written = append(written, dest)
	}
	return written, nil
}

func checkSkillDestConflict(destDir string, force bool) error {
	if force {
		return nil
	}
	entries, err := os.ReadDir(destDir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	if len(entries) > 0 {
		return fmt.Errorf("%s already exists and is not empty (use --force to overwrite)", destDir)
	}
	return nil
}
