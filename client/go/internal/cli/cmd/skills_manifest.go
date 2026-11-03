// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
)

const skillsLockFile = "skills-lock.json"

type installedSkill struct {
	SkillName string   `json:"name"`
	Harnesses []string `json:"harnesses"`
}

type skillsManifest struct {
	Ref    string           `json:"ref"`
	Skills []installedSkill `json:"skills"`
}

func skillsManifestPath(homeDir string) string { return filepath.Join(homeDir, skillsLockFile) }

func skillsManifestExists(homeDir string) bool {
	_, err := os.Stat(skillsManifestPath(homeDir))
	return err == nil
}

func readSkillsManifest(homeDir string) (*skillsManifest, error) {
	path := skillsManifestPath(homeDir)
	b, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return &skillsManifest{}, nil
	} else if err != nil {
		return nil, err
	}
	var m skillsManifest
	if err := json.Unmarshal(b, &m); err != nil {
		return nil, fmt.Errorf("could not parse %s: %w", path, err)
	}
	return &m, nil
}

func writeSkillsManifest(homeDir string, m *skillsManifest) error {
	if err := os.MkdirAll(homeDir, 0o700); err != nil {
		return err
	}
	b, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return err
	}
	b = append(b, '\n')
	return os.WriteFile(skillsManifestPath(homeDir), b, 0o600)
}

func (m *skillsManifest) merge(ref, skillName string, harnessIDs []string) {
	m.Ref = ref
	for i, s := range m.Skills {
		if s.SkillName == skillName {
			m.Skills[i].Harnesses = mergeSortedStrings(s.Harnesses, harnessIDs)
			return
		}
	}
	m.Skills = append(m.Skills, installedSkill{SkillName: skillName, Harnesses: mergeSortedStrings(nil, harnessIDs)})
	sort.Slice(m.Skills, func(i, j int) bool { return m.Skills[i].SkillName < m.Skills[j].SkillName })
}

func mergeSortedStrings(existing, add []string) []string {
	set := make(map[string]bool)
	for _, s := range existing {
		set[s] = true
	}
	for _, s := range add {
		set[s] = true
	}
	out := make([]string, 0, len(set))
	for s := range set {
		out = append(out, s)
	}
	sort.Strings(out)
	return out
}
