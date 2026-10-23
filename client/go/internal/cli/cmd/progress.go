// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"os"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode"

	"github.com/fatih/color"
	"github.com/mattn/go-runewidth"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"golang.org/x/term"
)

// shortcut: host bootstrapping counts are only available in this log message; replace with structured fields when the API supplies them.
var hostCountsPattern = regexp.MustCompile(`(?:^|\s)(\d+)/(\d+) application hosts(?: and (\d+)/(\d+) admin hosts)? for \S+ have completed provisioning and bootstrapping`)

type hostCount struct {
	label        string
	ready, total int64
}

func parseHostCounts(message string) []hostCount {
	match := hostCountsPattern.FindStringSubmatch(message)
	if match == nil {
		return nil
	}
	var counts []hostCount
	for i, label := range []string{"Application hosts", "Admin hosts"} {
		offset := 1 + 2*i
		if match[offset] == "" {
			continue
		}
		ready, err := strconv.ParseInt(match[offset], 10, 64)
		if err != nil {
			return nil
		}
		total, err := strconv.ParseInt(match[offset+1], 10, 64)
		if err != nil || ready > total {
			return nil
		}
		counts = append(counts, hostCount{label, ready, total})
	}
	return counts
}

type progressRun struct {
	id       int64
	snapshot vespa.RunProgress
	hosts    map[string][]hostCount
	updated  time.Time
	latestAt int64
	activity string
}

const progressRefreshInterval = 100 * time.Millisecond

var progressSpinnerFrames = [...]string{"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"}

type deploymentProgress struct {
	mu                   sync.Mutex
	stop                 chan struct{}
	done                 chan struct{}
	stopOnce             sync.Once
	cli                  *CLI
	title, url, activity string
	started              time.Time
	run                  progressRun
	jobs                 []vespa.BuildJobProgress
	runs                 map[string]*progressRun
	warnings             map[string]string
	recentWarnings       []string
	lines                int
	closed               bool
	size                 func() (int, int)
}

func (c *CLI) startDeploymentProgress(title string) *deploymentProgress {
	enabled, _ := c.config.get(progressFlag)
	_, screwdriver := c.Environment["SCREWDRIVER"]
	if enabled != "true" || c.config.isQuiet() || !c.isTerminal() || screwdriver || c.Environment["TERM"] == "dumb" {
		return nil
	}
	target, err := c.targetType(anyTarget)
	if err != nil || target.url != "" || target.name == vespa.TargetLocal {
		return nil
	}
	p := &deploymentProgress{
		cli: c, title: title, started: c.now(),
		stop: make(chan struct{}), done: make(chan struct{}),
		runs: map[string]*progressRun{}, warnings: map[string]string{},
		size: func() (int, int) {
			width, height, err := term.GetSize(int(os.Stderr.Fd()))
			if err != nil {
				return 80, 24
			}
			return width, height
		},
	}
	c.progress = p
	go p.animate()
	return p
}

func (p *deploymentProgress) animate() {
	ticker := time.NewTicker(progressRefreshInterval)
	defer ticker.Stop()
	defer close(p.done)
	for {
		select {
		case <-p.stop:
			return
		case <-ticker.C:
			p.mu.Lock()
			// Wait for the first progress update so authentication prompts stay undisturbed.
			if p.lines > 0 {
				p.render()
			}
			p.mu.Unlock()
		}
	}
}

func (p *deploymentProgress) stopAnimation() {
	p.stopOnce.Do(func() { close(p.stop) })
	<-p.done
}

func (p *deploymentProgress) setTitle(title string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.title = title
}

func (p *deploymentProgress) setURL(url string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.url = url
}

func (p *deploymentProgress) note(message string) {
	if p == nil {
		return
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed {
		return
	}
	p.activity = cleanProgressText(message)
	p.render()
}

func (p *deploymentProgress) record(run *progressRun, id int64, snapshot vespa.RunProgress, key string) {
	if run.id != id || run.hosts == nil {
		*run = progressRun{id: id, hosts: map[string][]hostCount{}}
	}
	run.snapshot = snapshot
	type entry struct {
		step string
		log  vespa.RunLogEntry
	}
	var logs []entry
	for step, messages := range snapshot.Log {
		for _, message := range messages {
			logs = append(logs, entry{step, message})
		}
	}
	sort.SliceStable(logs, func(i, j int) bool {
		if logs[i].log.At == logs[j].log.At {
			return logs[i].step < logs[j].step
		}
		return logs[i].log.At < logs[j].log.At
	})
	for _, entry := range logs {
		msg := entry.log
		if entry.step == "deployReal" || entry.step == "deployInitialReal" {
			if counts := parseHostCounts(msg.Message); counts != nil {
				run.hosts[entry.step] = counts
				run.updated = time.UnixMilli(msg.At)
			}
		}
		if entry.step != "copyVespaLogs" && msg.Type != "debug" && msg.At >= run.latestAt {
			run.latestAt = msg.At
			run.activity = cleanProgressText(msg.Message)
		}
		if msg.Type == "warning" || msg.Type == "error" {
			id := fmt.Sprintf("%s/%d/%s/%d/%s/%s", key, run.id, entry.step, msg.At, msg.Type, msg.Message)
			label := "Warning: "
			if msg.Type == "error" {
				label = "Error: "
			}
			if _, seen := p.warnings[id]; !seen {
				p.warnings[id] = label + cleanProgressText(msg.Message)
				p.recentWarnings = append(p.recentWarnings, p.warnings[id])
				p.recentWarnings = p.recentWarnings[max(0, len(p.recentWarnings)-2):]
			}
		}
	}
}

func (p *deploymentProgress) updateRun(id int64, snapshot vespa.RunProgress) {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return
	}
	p.record(&p.run, id, snapshot, "dev")
	p.activity = ""
	p.render()
	failed := !snapshot.Active && snapshot.Status != "success"
	if failed {
		p.finishLocked("✗ Deployment ended: " + snapshot.Status)
	}
	p.mu.Unlock()
	if failed {
		p.stopAnimation()
	}
}

func progressJobKey(job vespa.BuildJobProgress) string {
	return fmt.Sprintf("%s/%s/%d", job.Instance, job.JobName, job.RunID)
}

func (p *deploymentProgress) updateBuild(snapshot vespa.BuildProgress) {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return
	}
	p.jobs = snapshot.Jobs
	current := map[string]*progressRun{}
	for _, job := range p.jobs {
		if run := p.runs[progressJobKey(job)]; run != nil {
			current[progressJobKey(job)] = run
		}
	}
	p.runs = current
	p.activity = ""
	if len(p.jobs) == 0 {
		p.activity = "Waiting for pipeline jobs"
	}
	for _, job := range p.jobs {
		key := progressJobKey(job)
		if job.Details != nil {
			run := p.runs[key]
			if run == nil {
				run = &progressRun{}
				p.runs[key] = run
			}
			p.record(run, job.RunID, *job.Details, key)
		}
	}
	p.render()
	if snapshot.HasFailed {
		p.finishLocked("✗ Build reported a failure; fetching deployment diagnostics…")
	}
	p.mu.Unlock()
	if snapshot.HasFailed {
		p.stopAnimation()
	}
}

var progressSteps = []struct{ name, label string }{
	{"deployTester", "Deploy tester"}, {"installTester", "Install tester"},
	{"deployInitialReal", "Deploy initial application"}, {"installInitialReal", "Install initial application"},
	{"startStagingSetup", "Start staging setup"}, {"endStagingSetup", "Run staging setup"},
	{"deployReal", "Provision and deploy application"}, {"installReal", "Install application"},
	{"startTests", "Start tests"}, {"startProductionTests", "Run production tests"},
	{"endTests", "Run tests"}, {"copyVespaLogs", "Collect deployment logs"},
	{"deactivateReal", "Deactivate test application"}, {"deactivateTester", "Deactivate tester"},
	{"report", "Report result"},
}

func (p *deploymentProgress) spinnerFrame() string {
	frame := max(0, int(p.cli.now().Sub(p.started)/progressRefreshInterval))
	return progressSpinnerFrames[frame%len(progressSpinnerFrames)]
}

func (p *deploymentProgress) status(status string, started bool) string {
	switch status {
	case "success", "succeeded", "noTests":
		return "✓ Succeeded"
	case "running":
		return p.spinnerFrame() + " Running"
	case "unfinished":
		if started {
			return p.spinnerFrame() + " Running"
		}
		return "· Waiting"
	case "":
		return "· Waiting"
	default:
		return "✗ " + cleanProgressText(status)
	}
}

func (p *deploymentProgress) runLines(run *progressRun, compact bool) []string {
	var lines []string
	width, _ := p.size()
	seen := map[string]bool{}
	for _, step := range progressSteps {
		state, ok := run.snapshot.Steps[step.name]
		if !ok {
			continue
		}
		seen[step.name] = true
		// Production jobs share the panel: omit completed and not-yet-started steps.
		if compact && (state.Status == "succeeded" || state.Status == "unfinished" && state.StartMillis == 0) {
			continue
		}
		elapsed := ""
		if state.Status == "unfinished" && state.StartMillis > 0 {
			elapsed = " · " + progressDuration(p.cli.now().Sub(time.UnixMilli(state.StartMillis)))
		}
		lines = append(lines, fmt.Sprintf("%-36s %s%s", step.label, p.status(state.Status, state.StartMillis > 0), elapsed))
	}
	var unknown []string
	for name := range run.snapshot.Steps {
		if !seen[name] {
			unknown = append(unknown, name)
		}
	}
	sort.Strings(unknown)
	for _, name := range unknown {
		state := run.snapshot.Steps[name]
		lines = append(lines, name+"  "+p.status(state.Status, state.StartMillis > 0))
	}
	for _, step := range []string{"deployInitialReal", "deployReal"} {
		state, ok := run.snapshot.Steps[step]
		if !ok || state.Status != "unfinished" {
			continue
		}
		counts := run.hosts[step]
		if len(counts) == 0 {
			continue
		}
		age := progressDuration(p.cli.now().Sub(run.updated))
		lines = append(lines, "Host provisioning and bootstrapping · last reported "+age+" ago")
		for _, count := range counts {
			lines = append(lines, progressBar(count.label, count.ready, count.total, width-2))
		}
	}
	for _, name := range []string{"installInitialReal", "installReal"} {
		step, ok := run.snapshot.Steps[name]
		if !ok || step.Status != "unfinished" || step.Convergence == nil {
			continue
		}
		c := step.Convergence
		if c.Services > 0 && c.NeedNewConfig >= 0 && c.NeedNewConfig <= c.Services {
			lines = append(lines, progressBar("Service configuration", c.Services-c.NeedNewConfig, c.Services, width-2))
		}
		lines = append(lines, fmt.Sprintf("Nodes: %d · booting: %d · upgrading: %d · need upgrade: %d", c.Nodes, c.Booting, c.Upgrading, c.NeedPlatformUpgrade))
		lines = append(lines, fmt.Sprintf("Restarting: %d · need restart: %d · rebooting: %d · need reboot: %d", c.Restarting, c.NeedRestart, c.Rebooting, c.NeedReboot))
		if c.Retiring > 0 || c.UpgradingOs > 0 || c.UpgradingFirmware > 0 {
			lines = append(lines, fmt.Sprintf("Retiring: %d · upgrading OS: %d · upgrading firmware: %d", c.Retiring, c.UpgradingOs, c.UpgradingFirmware))
		}
	}
	if run.activity != "" {
		lines = append(lines, "Latest: "+run.activity)
	}
	return lines
}

func progressDuration(duration time.Duration) string {
	seconds := max(0, int(duration.Seconds()))
	return fmt.Sprintf("%02d:%02d", seconds/60, seconds%60)
}

func progressBar(label string, ready, total int64, width int) string {
	if total <= 0 {
		return label + "  not applicable"
	}
	prefix := fmt.Sprintf("%-21s %d/%d", label, ready, total)
	barWidth := min(18, width-runewidth.StringWidth(prefix)-4)
	if barWidth < 1 {
		return prefix
	}
	filled := int(float64(max(0, min(ready, total))) / float64(total) * float64(barWidth))
	return prefix + " [" + strings.Repeat("█", filled) + strings.Repeat("░", barWidth-filled) + "]"
}

func (p *deploymentProgress) content() []string {
	lines := []string{p.title + " · elapsed " + progressDuration(p.cli.now().Sub(p.started))}
	if p.run.id > 0 {
		lines = append(lines, fmt.Sprintf("Run %d", p.run.id))
		lines = append(lines, p.runLines(&p.run, false)...)
	}
	if p.jobs != nil {
		// Keep all active jobs ahead of completed rows when the terminal is short.
		jobs := append([]vespa.BuildJobProgress(nil), p.jobs...)
		sort.SliceStable(jobs, func(i, j int) bool { return jobs[i].RunStatus == "running" && jobs[j].RunStatus != "running" })
		for _, job := range jobs {
			lines = append(lines, fmt.Sprintf("%s / %s · run %d  %s", job.Instance, job.JobName, job.RunID, p.status(job.RunStatus, true)))
			if job.RunStatus != "running" {
				continue
			}
			if run := p.runs[progressJobKey(job)]; run != nil {
				for _, line := range p.runLines(run, true) {
					lines = append(lines, "  "+line)
				}
			}
			if job.DetailsUnavailable {
				lines = append(lines, "  Details unavailable; waiting for next update")
			}
		}
	}
	if p.activity != "" {
		lines = append(lines, p.activity)
	}
	if len(p.warnings) > 0 {
		lines = append(lines, fmt.Sprintf("Warnings/errors: %d", len(p.warnings)))
		lines = append(lines, p.recentWarnings...)
	}
	if p.url != "" {
		lines = append(lines, "Console: "+p.url)
	}
	return lines
}

func cleanProgressText(text string) string {
	// Drop terminal escape sequences, including OSC links, before removing other controls.
	var out strings.Builder
	for i := 0; i < len(text); {
		if text[i] == '\x1b' {
			i++
			if i >= len(text) {
				break
			}
			switch text[i] {
			case '[':
				i++
				for i < len(text) {
					c := text[i]
					i++
					if c >= 0x40 && c <= 0x7e {
						break
					}
				}
			case ']', 'P', '_', '^':
				i++
				for i < len(text) {
					if text[i] == '\a' {
						i++
						break
					}
					if text[i] == '\x1b' && i+1 < len(text) && text[i+1] == '\\' {
						i += 2
						break
					}
					i++
				}
			default:
				i++
			}
			continue
		}
		start := i
		i++
		for i < len(text) && text[i] != '\x1b' {
			i++
		}
		for _, r := range text[start:i] {
			if unicode.IsControl(r) {
				out.WriteByte(' ')
			} else if !unicode.Is(unicode.Cf, r) {
				out.WriteRune(r)
			}
		}
	}
	return out.String()
}

func (p *deploymentProgress) render() {
	if p == nil || p.closed {
		return
	}
	width, height := p.size()
	width, height = max(1, width-1), max(2, height)
	lines := p.content()
	if len(lines) > height-1 {
		footer := []string{}
		if p.activity != "" {
			footer = append(footer, p.activity)
		}
		if len(p.warnings) > 0 {
			footer = append(footer, fmt.Sprintf("Warnings/errors: %d · see Console for details", len(p.warnings)))
		}
		if p.url != "" {
			footer = append(footer, "Console: "+p.url)
		}
		keep := max(0, height-2-len(footer))
		hidden := len(lines) - len(footer) - keep
		lines = append(lines[:keep], fmt.Sprintf("… %d detail lines hidden", hidden))
		lines = append(lines, footer...)
		if len(lines) > height-1 {
			lines = lines[len(lines)-(height-1):]
		}
	}
	var frame strings.Builder
	if p.lines > 0 {
		fmt.Fprintf(&frame, "\x1b[%dA\r\x1b[J", min(p.lines, max(1, height-1)))
	}
	for _, line := range lines {
		line = runewidth.Truncate(cleanProgressText(line), width, "…")
		paint := color.New()
		switch {
		case strings.Contains(line, "✗"), strings.HasPrefix(strings.TrimSpace(line), "Error:"):
			paint = color.New(color.FgRed)
		case strings.HasPrefix(strings.TrimSpace(line), "Warning"), strings.HasPrefix(line, "Monitoring stopped"):
			paint = color.New(color.FgYellow)
		case strings.HasPrefix(strings.TrimSpace(line), "Admin hosts"):
			paint = color.New(color.FgMagenta)
		case strings.HasPrefix(strings.TrimSpace(line), "Application hosts"), strings.HasPrefix(strings.TrimSpace(line), "Service configuration"), strings.Contains(line, " Running"):
			paint = color.New(color.FgCyan)
		case strings.Contains(line, "✓"):
			paint = color.New(color.FgGreen)
		case strings.Contains(line, "· Waiting"):
			paint = color.New(color.Faint)
		}
		if color.NoColor {
			paint.DisableColor()
		} else {
			paint.EnableColor()
		}
		fmt.Fprintln(&frame, paint.Sprint(line))
	}
	fmt.Fprint(p.cli.Stderr, frame.String())
	p.lines = len(lines)
}

func (p *deploymentProgress) finish(message string) {
	if p == nil {
		return
	}
	p.mu.Lock()
	p.finishLocked(message)
	p.mu.Unlock()
	p.stopAnimation()
}

func (p *deploymentProgress) finishLocked(message string) {
	if p.closed {
		return
	}
	p.activity = message
	// Print the complete URL after the last redraw so long links remain usable.
	url := p.url
	p.url = ""
	p.render()
	p.url = url
	p.closed = true
	if url != "" {
		fmt.Fprintln(p.cli.Stderr, "Console: "+cleanProgressText(url))
	}
}

func (p *deploymentProgress) close() {
	if p == nil {
		return
	}
	p.mu.Lock()
	if !p.closed && p.lines > 0 {
		p.finishLocked("Monitoring stopped; deployment may still be running")
	}
	p.closed = true
	p.mu.Unlock()
	p.stopAnimation()
	p.cli.progress = nil
}
