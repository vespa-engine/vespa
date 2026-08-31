// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"archive/zip"
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/spf13/cobra"
)

func newSkillsInstallCmd(cli *CLI) *cobra.Command {
	var (
		harnessArg []string
		globalArg  bool
		localArg   bool
		force      bool
	)
	cmd := &cobra.Command{
		Use:   "install [skill]...",
		Short: "Install Vespa AI-assistant skills",
		Long: `Install Vespa AI-assistant skills for one or more agent harnesses.

Skills are downloaded from https://github.com/vespa-engine/skills and
copied into the directory the chosen harness(es) discover automatically. Run
'vespa skills list' to see available skills.

If no skill names are given, all available skills are installed. If
--harness or --local/--global are not given and the terminal is interactive,
you will be prompted to choose.`,
		Example: `$ vespa skills install
$ vespa skills install schema-authoring app-package
$ vespa skills install --harness claude,codex --local`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return runSkillsInstall(cli, args, harnessArg, globalArg, localArg, force)
		},
	}
	cmd.Flags().StringSliceVar(&harnessArg, "harness", nil, fmt.Sprintf("Agent harness(es) to install for (%s). Can be repeated or comma-separated", strings.Join(skillHarnessNames(), ", ")))
	cmd.Flags().BoolVarP(&globalArg, "global", "g", false, "Install for all projects, in the user's home directory")
	cmd.Flags().BoolVarP(&localArg, "local", "l", false, "Install for the current project only")
	cmd.Flags().BoolVarP(&force, "force", "f", false, "Ignore cache when downloading, and overwrite already-installed skills")
	return cmd
}

func runSkillsInstall(cli *CLI, skillArgs, harnessArg []string, globalArg, localArg, force bool) error {
	if globalArg && localArg {
		return fmt.Errorf("cannot use both --global and --local flags")
	}
	source := &skillsSource{cli: cli, noCache: force}
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
	selected, err := resolveSkillSelection(available, skillArgs)
	if err != nil {
		return err
	}
	stdin := bufio.NewReader(cli.Stdin)
	selectedHarnesses, err := resolveHarnessSelection(cli, stdin, harnessArg)
	if err != nil {
		return err
	}
	useLocal, err := resolveScopeSelection(cli, stdin, globalArg, localArg)
	if err != nil {
		return err
	}
	ref := skillsArchiveRef(zipPath)
	installedPaths, err := installSkills(cli, &archive.Reader, ref, selected, selectedHarnesses, useLocal, force)
	if err != nil {
		return err
	}
	names := make([]string, len(selected))
	for i, s := range selected {
		names[i] = s.name
	}
	hnames := make([]string, len(selectedHarnesses))
	for i, h := range selectedHarnesses {
		hnames[i] = h.displayName
	}
	cli.printSuccess(fmt.Sprintf("Installed %s for %s", strings.Join(names, ", "), strings.Join(hnames, ", ")))
	for _, p := range installedPaths {
		cli.printInfo("  " + p)
	}
	return nil
}

func resolveSkillSelection(available []skillMeta, args []string) ([]skillMeta, error) {
	if len(args) == 0 {
		return available, nil
	}
	byName := make(map[string]skillMeta)
	for _, s := range available {
		byName[s.name] = s
	}
	var selected []skillMeta
	var unknown []string
	for _, name := range args {
		s, ok := byName[name]
		if !ok {
			unknown = append(unknown, name)
			continue
		}
		selected = append(selected, s)
	}
	if len(unknown) > 0 {
		return nil, errHint(fmt.Errorf("unknown skill(s): %s", strings.Join(unknown, ", ")), "Run 'vespa skills list' to see available skills")
	}
	return selected, nil
}

func resolveHarnessSelection(cli *CLI, stdin *bufio.Reader, harnessArg []string) ([]skillHarness, error) {
	if len(harnessArg) > 0 {
		var selected []skillHarness
		var unknown []string
		for _, id := range harnessArg {
			id = strings.ToLower(strings.TrimSpace(id))
			h, ok := skillHarnessByID(id)
			if !ok {
				unknown = append(unknown, id)
				continue
			}
			selected = append(selected, h)
		}
		if len(unknown) > 0 {
			return nil, errHint(fmt.Errorf("unknown harness(es): %s", strings.Join(unknown, ", ")), "Valid harnesses: "+strings.Join(skillHarnessNames(), ", "))
		}
		return selected, nil
	}
	if !cli.isTerminal() {
		return nil, errHint(fmt.Errorf("no harness specified"), "Use --harness to specify one or more agent harnesses non-interactively", "Valid harnesses: "+strings.Join(skillHarnessNames(), ", "))
	}
	return promptHarnessSelection(cli, stdin)
}

func promptHarnessSelection(cli *CLI, stdin *bufio.Reader) ([]skillHarness, error) {
	fmt.Fprintln(cli.Stdout, "Select agent harness(es) to install Vespa skills for:")
	for i, h := range skillHarnesses {
		fmt.Fprintf(cli.Stdout, "  [%d] %s\n", i+1, h.displayName)
	}
	validator := func(input string) error {
		_, err := parseHarnessSelection(input)
		return err
	}
	answer, err := prompt(cli, stdin, "Enter number(s) separated by commas (e.g. 1,3), or 'a' for all:", "", validator)
	if err != nil {
		return nil, err
	}
	return parseHarnessSelection(answer)
}

func parseHarnessSelection(answer string) ([]skillHarness, error) {
	if strings.EqualFold(answer, "a") {
		return append([]skillHarness(nil), skillHarnesses...), nil
	}
	parts := strings.Split(answer, ",")
	selected := make([]skillHarness, 0, len(parts))
	for _, p := range parts {
		n, err := strconv.Atoi(strings.TrimSpace(p))
		if err != nil || n < 1 || n > len(skillHarnesses) {
			return nil, fmt.Errorf("invalid selection, please try again")
		}
		selected = append(selected, skillHarnesses[n-1])
	}
	return selected, nil
}

func resolveScopeSelection(cli *CLI, stdin *bufio.Reader, globalArg, localArg bool) (bool, error) {
	if globalArg {
		return false, nil
	}
	if localArg {
		return true, nil
	}
	if !cli.isTerminal() {
		return false, errHint(fmt.Errorf("no install scope specified"), "Use --local to install for the current project, or --global to install for all projects")
	}
	return promptScopeSelection(cli, stdin)
}

func promptScopeSelection(cli *CLI, stdin *bufio.Reader) (bool, error) {
	validator := func(input string) error {
		switch strings.ToLower(input) {
		case "p", "g":
			return nil
		}
		return fmt.Errorf("please answer 'p' or 'g'")
	}
	answer, err := prompt(cli, stdin, "Install for this project only, or globally for all projects? [p/g]", "p", validator)
	if err != nil {
		return false, err
	}
	return strings.EqualFold(answer, "p"), nil
}

func installRoot(cli *CLI, useLocal bool) (string, error) {
	if useLocal {
		return filepath.Dir(cli.config.local.homeDir), nil
	}
	return os.UserHomeDir()
}

func manifestHomeDir(cli *CLI, useLocal bool) string {
	if useLocal {
		return cli.config.local.homeDir
	}
	return cli.config.homeDir
}

func installSkills(cli *CLI, r *zip.Reader, ref string, skills []skillMeta, harnesses []skillHarness, useLocal, force bool) ([]string, error) {
	root, err := installRoot(cli, useLocal)
	if err != nil {
		return nil, err
	}
	manifestHome := manifestHomeDir(cli, useLocal)
	manifest, err := readSkillsManifest(manifestHome)
	if err != nil {
		return nil, err
	}

	harnessIDs := make([]string, 0, len(harnesses))
	for _, h := range harnesses {
		harnessIDs = append(harnessIDs, h.id)
	}

	var installedPaths []string
	for _, s := range skills {
		paths, err := extractSkillToHarnesses(r, s.name, harnesses, root, true, force)
		if err != nil {
			return installedPaths, err
		}
		installedPaths = append(installedPaths, paths...)
		manifest.merge(ref, s.name, harnessIDs)
		// Persist after each skill so a later failure doesn't leave already-extracted skills untracked.
		if err := writeSkillsManifest(manifestHome, manifest); err != nil {
			return installedPaths, err
		}
	}
	return installedPaths, nil
}
