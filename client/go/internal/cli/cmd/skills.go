// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa skills command
package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
)

const (
	skillsRepoOwner = "vespaai-playground"
	skillsRepoName  = "skills"
	skillsRepoRef   = "main"
)

type skillHarness struct {
	id          string
	displayName string
	dir         string
}

var skillHarnesses = []skillHarness{
	{id: "claude", displayName: "Claude Code", dir: filepath.Join(".claude", "skills")},
	{id: "codex", displayName: "Codex", dir: filepath.Join(".agents", "skills")},
	{id: "antigravity", displayName: "Antigravity CLI", dir: filepath.Join(".agents", "skills")},
	{id: "cursor", displayName: "Cursor", dir: filepath.Join(".cursor", "skills")},
}

func skillHarnessByID(id string) (skillHarness, bool) {
	for _, h := range skillHarnesses {
		if h.id == id {
			return h, true
		}
	}
	return skillHarness{}, false
}

func skillHarnessNames() []string {
	names := make([]string, len(skillHarnesses))
	for i, h := range skillHarnesses {
		names[i] = h.id
	}
	return names
}

func skillHarnessDisplayList() string {
	names := make([]string, len(skillHarnesses))
	for i, h := range skillHarnesses {
		names[i] = h.displayName
	}
	if len(names) == 1 {
		return names[0]
	}
	return strings.Join(names[:len(names)-1], ", ") + " or " + names[len(names)-1]
}

const skillsPromptedMarker = "skills-prompted"

// maybePromptSkillsInstall offers to install Vespa AI-assistant skills the first time any vespa command is run.
func (c *CLI) maybePromptSkillsInstall(cmd *cobra.Command) {
	if cmd == c.cmd { // Bare 'vespa' invocation, don't prompt here
		return
	}
	if !c.isTerminal() || c.config.isQuiet() || c.isCI() {
		return
	}
	for p := cmd; p != nil; p = p.Parent() {
		switch p.Name() {
		case "skills", "version", "help", "completion", "man", "gendoc":
			return
		}
	}
	marker := filepath.Join(c.config.homeDir, skillsPromptedMarker)
	if _, err := os.Stat(marker); !os.IsNotExist(err) {
		return
	}
	// Write the marker immediately, so this never prompts twice - even if the user declines, or something below fails.
	if err := os.WriteFile(marker, []byte("1\n"), 0o600); err != nil {
		return
	}
	if skillsManifestExists(c.config.homeDir) || (c.config.local != nil && skillsManifestExists(c.config.local.homeDir)) {
		// Don't prompt when skills already installed
		return
	}
	fmt.Fprintln(c.Stdout)
	question := fmt.Sprintf("Vespa AI-assistant skills make developing Vespa applications easier.\nInstall them for %s?", skillHarnessDisplayList())
	yes, err := c.confirm(question, true)
	if err != nil {
		return
	}
	if !yes {
		c.printHelpfulInfo("You can install these later by running 'vespa skills install', or by running 'npx skills add vespaai-playground/skills'")
		return
	}
	if err := runSkillsInstall(c, nil, nil, false, false, false); err != nil {
		c.printWarning(err, "Run 'vespa skills install' to try again")
	}
}

func newSkillsCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "skills",
		Short: "Manage Vespa AI-assistant skills",
		Long: `Manage Vespa AI-assistant skills.

Skills teach AI coding assistants how to work with Vespa: schema authoring,
application packages, queries, feeding and more. Skills are downloaded from
https://github.com/vespaai-playground/skills and installed for one or more
agent harnesses: Claude Code, Codex, Cursor and Antigravity CLI.`,
		DisableAutoGenTag: true,
		SilenceUsage:      false,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}
}
