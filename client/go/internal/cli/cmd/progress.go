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

type progressCount struct {
	label        string
	ready, total int64
}

func parseHostCounts(message string) []progressCount {
	match := hostCountsPattern.FindStringSubmatch(message)
	if match == nil {
		return nil
	}
	var counts []progressCount
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
		counts = append(counts, progressCount{label, ready, total})
	}
	return counts
}

type progressRun struct {
	id       int64
	snapshot vespa.RunProgress
	hosts    map[string][]progressCount
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
	warnings             map[string]bool // dedup keys for warningLog
	warningLog           []string
	copyLogLevel         int // like printLog, copyVespaLogs entries are filtered by --log-level
	lines                int
	final, closed        bool
	size                 func() (int, int)
}

func (c *CLI) startDeploymentProgress(title string) *deploymentProgress {
	enabled, _ := c.config.get(progressFlag)
	_, screwdriver := c.Environment["SCREWDRIVER"]
	if enabled != "true" || c.config.isQuiet() || !c.isTerminal() || c.isCI() || screwdriver || c.Environment["TERM"] == "dumb" {
		return nil
	}
	target, err := c.targetType(anyTarget)
	if err != nil || target.url != "" || target.name == vespa.TargetLocal {
		return nil
	}
	p := &deploymentProgress{
		cli: c, title: title, started: c.now(),
		stop: make(chan struct{}), done: make(chan struct{}),
		runs: map[string]*progressRun{}, warnings: map[string]bool{}, copyLogLevel: vespa.LogLevel("debug"),
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
	if p == nil {
		return
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	p.title = title
}

func (p *deploymentProgress) setURL(url string) {
	if p == nil {
		return
	}
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
		*run = progressRun{id: id, hosts: map[string][]progressCount{}}
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
		if (msg.Type == "warning" || msg.Type == "error") && (entry.step != "copyVespaLogs" || vespa.LogLevel(msg.Type) <= p.copyLogLevel) {
			id := fmt.Sprintf("%s/%d/%s/%d/%s/%s", key, run.id, entry.step, msg.At, msg.Type, msg.Message)
			label := "Warning: "
			if msg.Type == "error" {
				label = "Error: "
			}
			if !p.warnings[id] {
				p.warnings[id] = true
				p.warningLog = append(p.warningLog, label+cleanProgressText(msg.Message))
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
		p.finishLocked("✗ Deployment ended: "+snapshot.Status, false)
	}
	p.mu.Unlock()
	if failed {
		p.stopAnimation()
	}
}

func (p *deploymentProgress) updateBuild(snapshot vespa.BuildProgress) {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return
	}
	p.jobs = snapshot.Jobs
	// Keep only runs of current jobs.
	current := map[string]*progressRun{}
	for _, job := range p.jobs {
		key := job.Key()
		run := p.runs[key]
		if run == nil && job.Details != nil {
			run = &progressRun{}
		}
		if run != nil {
			current[key] = run
		}
		if job.Details != nil {
			p.record(run, job.RunID, *job.Details, key)
		}
	}
	p.runs = current
	p.activity = ""
	if len(p.jobs) == 0 {
		p.activity = "Waiting for pipeline jobs"
	}
	p.render()
	if snapshot.HasFailed {
		p.finishLocked("✗ Build reported a failure; fetching deployment diagnostics…", false)
	}
	p.mu.Unlock()
	if snapshot.HasFailed {
		p.stopAnimation()
	}
}

var progressSteps = []struct{ name, label string }{
	{"deployTester", "Deploy tester"},
	{"installTester", "Install tester"},
	{"deployInitialReal", "Deploy initial application"},
	{"installInitialReal", "Install initial application"},
	{"startStagingSetup", "Start staging setup"},
	{"endStagingSetup", "Run staging setup"},
	{"deployReal", "Provision and deploy application"},
	{"installReal", "Install application"},
	{"startTests", "Start tests"},
	{"startProductionTests", "Run production tests"},
	{"endTests", "Run tests"},
	{"copyVespaLogs", "Collect deployment logs"},
	{"deactivateReal", "Deactivate test application"},
	{"deactivateTester", "Deactivate tester"},
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

func (p *deploymentProgress) runLines(run *progressRun, compact bool, labelWidth int) []string {
	var lines []string
	width, _ := p.size()
	width-- // Leave the last terminal column unused to avoid automatic line wrapping.
	if compact {
		width -= 3
	}
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
		label := runewidth.FillRight(runewidth.Truncate(step.label, labelWidth, "…"), labelWidth)
		lines = append(lines, label+" "+p.status(state.Status, state.StartMillis > 0)+elapsed)
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
		label := runewidth.FillRight(runewidth.Truncate(cleanProgressText(name), labelWidth, "…"), labelWidth)
		lines = append(lines, label+" "+p.status(state.Status, state.StartMillis > 0))
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
		lines = append(lines, progressBars(width, counts...)...)
	}
	for _, name := range []string{"installInitialReal", "installReal"} {
		step, ok := run.snapshot.Steps[name]
		if !ok || step.Status != "unfinished" || step.Convergence == nil {
			continue
		}
		c := step.Convergence
		var counts []progressCount
		if c.Nodes > 0 && c.NeedPlatformUpgrade >= 0 && c.NeedPlatformUpgrade <= c.Nodes {
			counts = append(counts, progressCount{"Platform upgrade", c.Nodes - c.NeedPlatformUpgrade, c.Nodes})
		}
		if c.Services > 0 && c.NeedNewConfig >= 0 && c.NeedNewConfig <= c.Services {
			counts = append(counts, progressCount{"Service configuration", c.Services - c.NeedNewConfig, c.Services})
		}
		lines = append(lines, progressBars(width, counts...)...)
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

const progressBarSeparator = "  │  "

func progressBars(width int, counts ...progressCount) []string {
	var lines []string
	for _, count := range counts {
		bar := progressBar(count.label, count.ready, count.total, width)
		last := len(lines) - 1
		if last >= 0 && runewidth.StringWidth(lines[last]+progressBarSeparator+bar) <= width {
			lines[last] += progressBarSeparator + bar
		} else {
			lines = append(lines, bar)
		}
	}
	return lines
}

func (p *deploymentProgress) content() []string {
	return append(p.body(), p.footer(false)...)
}

func (p *deploymentProgress) body() []string {
	lines := []string{p.title + " · elapsed " + progressDuration(p.cli.now().Sub(p.started))}
	if p.run.id > 0 {
		lines = append(lines, fmt.Sprintf("Run %d", p.run.id))
		lines = append(lines, p.runLines(&p.run, false, 36)...)
	}
	if p.jobs != nil {
		// Keep all active jobs ahead of completed rows when the terminal is short.
		jobs := append([]vespa.BuildJobProgress(nil), p.jobs...)
		sort.SliceStable(jobs, func(i, j int) bool { return jobs[i].RunStatus == "running" && jobs[j].RunStatus != "running" })
		labels := make([]string, len(jobs))
		labelWidth := 36
		for i, job := range jobs {
			labels[i] = cleanProgressText(fmt.Sprintf("%s / %s · run %d", job.Instance, job.JobName, job.RunID))
			labelWidth = max(labelWidth, runewidth.StringWidth(labels[i]))
		}
		width, _ := p.size()
		// Reserve room for indentation, status and elapsed time on step rows.
		labelWidth = min(labelWidth, max(1, width-22))
		for i, job := range jobs {
			label := runewidth.Truncate(labels[i]+" ", labelWidth, "…")
			header := "── " + label + strings.Repeat("─", labelWidth-runewidth.StringWidth(label))
			lines = append(lines, "", header+" "+p.status(job.RunStatus, true))
			if job.RunStatus != "running" {
				continue
			}
			if run := p.runs[job.Key()]; run != nil {
				for _, line := range p.runLines(run, true, labelWidth) {
					lines = append(lines, "   "+line)
				}
			}
			if job.DetailsUnavailable {
				lines = append(lines, "   Details unavailable; waiting for next update")
			}
		}
	}
	return lines
}

// footer returns the activity, warnings and console lines; compact summarizes warnings on one line.
func (p *deploymentProgress) footer(compact bool) []string {
	var lines []string
	if p.activity != "" {
		lines = append(lines, p.activity)
	}
	if len(p.warningLog) > 0 && !p.final {
		if compact {
			lines = append(lines, fmt.Sprintf("Warnings/errors: %d · see Console for details", len(p.warningLog)))
		} else {
			lines = append(lines, fmt.Sprintf("Warnings/errors: %d", len(p.warningLog)))
			lines = append(lines, p.warningLog[max(0, len(p.warningLog)-2):]...)
		}
	}
	if p.url != "" && !p.final {
		lines = append(lines, "", "Console: "+p.url)
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
	if p.closed {
		return
	}
	width, height := p.size()
	width, height = max(1, width-1), max(2, height)
	lines := p.content()
	// A live frame must fit the screen to be redrawn in place; the final frame is never redrawn, so it is printed in full.
	if len(lines) > height-1 && !p.final {
		body, footer := p.body(), p.footer(true)
		keep := max(0, height-2-len(footer))
		lines = body
		if hidden := len(body) - keep; hidden > 0 {
			lines = append(body[:keep], fmt.Sprintf("… %d more lines shown when done", hidden))
		}
		lines = append(lines, footer...)
		if len(lines) > height-1 {
			lines = lines[len(lines)-(height-1):]
		}
	}
	var frame strings.Builder
	frame.WriteString(p.clearFrame())
	for _, line := range lines {
		line = runewidth.Truncate(cleanProgressText(line), width, "…")
		for i, part := range strings.Split(line, progressBarSeparator) {
			if i > 0 {
				frame.WriteString(progressBarSeparator)
			}
			fmt.Fprint(&frame, paintProgressLine(part))
		}
		frame.WriteByte('\n')
	}
	fmt.Fprint(p.cli.Stderr, frame.String())
	p.lines = len(lines)
}

// clearFrame returns the escape sequence that erases the previously drawn frame.
func (p *deploymentProgress) clearFrame() string {
	if p.lines == 0 {
		return ""
	}
	_, height := p.size()
	return fmt.Sprintf("\x1b[%dA\r\x1b[J", min(p.lines, max(1, height-1)))
}

// printAbove prints text above the live frame so the next redraw does not erase it.
func (p *deploymentProgress) printAbove(text string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed || p.lines == 0 {
		fmt.Fprintln(p.cli.Stderr, text)
		return
	}
	fmt.Fprintln(p.cli.Stderr, p.clearFrame()+text)
	p.lines = 0
	p.render()
}

func paintProgressLine(line string) string {
	paint := color.New()
	trimmed := strings.TrimSpace(line)
	switch {
	case strings.Contains(line, "✗"), strings.HasPrefix(trimmed, "Error:"):
		paint = color.New(color.FgRed)
	case strings.HasPrefix(trimmed, "Warning"), strings.HasPrefix(line, "Monitoring stopped"):
		paint = color.New(color.FgYellow)
	case strings.HasPrefix(trimmed, "Admin hosts"):
		paint = color.New(color.FgMagenta)
	case strings.HasPrefix(trimmed, "Platform upgrade"):
		paint = color.New(color.FgBlue)
	case strings.HasPrefix(trimmed, "Service configuration"):
		paint = color.New(color.FgGreen)
	case strings.HasPrefix(trimmed, "Application hosts"), strings.Contains(line, " Running"):
		paint = color.New(color.FgCyan)
	case strings.Contains(line, "✓"):
		paint = color.New(color.FgGreen)
	case strings.Contains(line, "· Waiting"):
		paint = color.New(color.Faint)
	}
	if strings.HasPrefix(line, "── ") {
		paint.Add(color.Bold)
	}
	return paint.Sprint(line)
}

func (p *deploymentProgress) finish(message string) {
	if p == nil {
		return
	}
	p.mu.Lock()
	p.finishLocked(message, true)
	p.mu.Unlock()
	p.stopAnimation()
}

// finishLocked draws the final frame, then prints the complete URL so long links remain usable.
// The frame only shows the latest warnings, so all of them are printed unless printWarnings is false.
// Failure paths pass false: AwaitDeployment and AwaitBuild print the full run log after a failure.
func (p *deploymentProgress) finishLocked(message string, printWarnings bool) {
	if p.closed {
		return
	}
	p.activity = message
	p.final = true
	p.render()
	p.closed = true
	if printWarnings && len(p.warningLog) > 0 {
		fmt.Fprintf(p.cli.Stderr, "\nWarnings/errors: %d\n", len(p.warningLog))
		for _, w := range p.warningLog {
			fmt.Fprintln(p.cli.Stderr, paintProgressLine(w))
		}
	}
	if p.url != "" {
		fmt.Fprintln(p.cli.Stderr, "\nConsole: "+cleanProgressText(p.url))
	}
}

// succeeded returns whether the observed dev run completed successfully.
func (p *deploymentProgress) succeeded() bool {
	return p.run.id > 0 && !p.run.snapshot.Active && p.run.snapshot.Status == "success"
}

func (p *deploymentProgress) endMessage() string {
	switch {
	case p.succeeded():
		return "✓ Deployment complete"
	case p.run.id > 0 && p.run.snapshot.Active:
		return fmt.Sprintf("Deployment run %d is still running", p.run.id)
	case p.url == "":
		// Nothing was submitted or observed yet.
		return "Monitoring stopped"
	}
	return "Monitoring stopped; deployment may still be running"
}

func (p *deploymentProgress) close() {
	if p == nil {
		return
	}
	p.mu.Lock()
	if !p.closed && p.lines > 0 {
		p.finishLocked(p.endMessage(), true)
	}
	p.closed = true
	p.mu.Unlock()
	p.stopAnimation()
	p.cli.progress = nil
}
