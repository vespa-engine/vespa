// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"slices"
	"strings"

	"github.com/spf13/cobra"
)

func newSkillsUpdateCmd(cli *CLI) *cobra.Command {
	var globalArg, localArg bool
	cmd := &cobra.Command{
		Use:   "update [skill]...",
		Short: "Update previously installed Vespa AI-assistant skills",
		Long: `Update previously installed Vespa AI-assistant skills to the latest version.

This replays whichever harness(es) and scope were used by a previous 'vespa
skills install', without prompting again. Run 'vespa skills install' first
if you haven't installed any skills yet.

If no skill names are given, every installed skill is checked for updates.`,
		Example:           "$ vespa skills update\n$ vespa skills update schema-authoring",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runSkillsUpdate(cli, args, globalArg, localArg)
		},
	}
	cmd.Flags().BoolVarP(&globalArg, "global", "g", false, "Only update globally installed skills")
	cmd.Flags().BoolVarP(&localArg, "local", "l", false, "Only update skills installed in the current project")
	cmd.MarkFlagsMutuallyExclusive("local", "global")
	return cmd
}

func runSkillsUpdate(cli *CLI, skillArgs []string, globalArg, localArg bool) error {
	var scopes []bool // true = local scope
	switch {
	case globalArg:
		scopes = []bool{false}
	case localArg:
		scopes = []bool{true}
	default:
		scopes = []bool{true, false}
	}

	source := &skillsSource{cli: cli, noCache: true}
	zipPath, err := source.zipPath()
	if err != nil {
		return err
	}
	archive, err := openSkillsZip(zipPath)
	if err != nil {
		return err
	}
	defer archive.Close()
	available, err := listAvailableSkills(&archive.Reader)
	if err != nil {
		return err
	}
	availableByName := make(map[string]skillMeta)
	for _, s := range available {
		availableByName[s.name] = s
	}
	ref := skillsArchiveRef(zipPath)

	foundManifest := false
	trackedSkills := make(map[string]bool)
	updated := 0
	for _, useLocal := range scopes {
		manifestHome := manifestHomeDir(cli, useLocal)
		manifest, err := readSkillsManifest(manifestHome)
		if err != nil {
			return err
		}
		if len(manifest.Skills) == 0 {
			continue
		}
		foundManifest = true
		for _, tracked := range manifest.Skills {
			trackedSkills[tracked.SkillName] = true
		}
		if manifest.Ref == ref {
			continue // already up to date for this scope
		}
		root, err := installRoot(cli, useLocal)
		if err != nil {
			return err
		}
		for _, tracked := range manifest.Skills {
			if len(skillArgs) > 0 && !slices.Contains(skillArgs, tracked.SkillName) {
				continue
			}
			skill, ok := availableByName[tracked.SkillName]
			if !ok {
				cli.printWarning(fmt.Sprintf("skill '%s' no longer exists upstream, skipping", tracked.SkillName))
				continue
			}
			harnesses := make([]skillHarness, 0, len(tracked.Harnesses))
			for _, hid := range tracked.Harnesses {
				if h, ok := skillHarnessByID(hid); ok {
					harnesses = append(harnesses, h)
				}
			}
			paths, err := extractSkillToHarnesses(&archive.Reader, skill.name, harnesses, root, false, false)
			if err != nil {
				return err
			}
			updated += len(paths)
		}
		manifest.Ref = ref
		if err := writeSkillsManifest(manifestHome, manifest); err != nil {
			return err
		}
	}
	if !foundManifest {
		return errHint(fmt.Errorf("no installed skills found"), "Run 'vespa skills install' first")
	}
	if len(skillArgs) > 0 {
		var unknown []string
		for _, name := range skillArgs {
			if !trackedSkills[name] {
				unknown = append(unknown, name)
			}
		}
		if len(unknown) > 0 {
			return errHint(fmt.Errorf("unknown skill(s): %s", strings.Join(unknown, ", ")), "Run 'vespa skills list' to see installed skills")
		}
	}
	if updated == 0 {
		cli.printInfo("Skills are already up to date")
	} else {
		cli.printSuccess(fmt.Sprintf("Updated %d skill installation(s)", updated))
	}
	return nil
}
