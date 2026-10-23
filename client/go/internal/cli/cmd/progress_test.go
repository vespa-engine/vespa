// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"
	"testing/synctest"
	"time"

	"github.com/fatih/color"
	"github.com/mattn/go-runewidth"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const provisioningMessage = "1/3 application hosts and 1/1 admin hosts for t1.a1.i1 have completed provisioning and bootstrapping, still waiting for host2, host3"

func TestParseHostCounts(t *testing.T) {
	assert.Equal(t, []progressCount{{"Application hosts", 1, 3}, {"Admin hosts", 1, 1}}, parseHostCounts(provisioningMessage))
	assert.Equal(t, []progressCount{{"Application hosts", 0, 3}}, parseHostCounts("0/3 application hosts for t1.a1.i1 have completed provisioning and bootstrapping, still waiting for host1"))
	for _, message := range []string{
		"1/3 application hosts", "3/1 application hosts for t.a.i have completed provisioning and bootstrapping",
		"0/1 application hosts and 2/1 admin hosts for t.a.i have completed provisioning and bootstrapping",
		"999999999999999999999999999/3 application hosts for t.a.i have completed provisioning and bootstrapping",
		"Waiting for 1/3 nodes", "-1/3 application hosts for t.a.i have completed provisioning and bootstrapping",
	} {
		assert.Empty(t, parseHostCounts(message), message)
	}
	assert.Contains(t, progressBar("Admin hosts", 0, 0, 80), "not applicable")
}

func newProgressTest(t *testing.T) (*deploymentProgress, *bytes.Buffer) {
	t.Helper()
	cli, _, stderr := newTestCLI(t)
	require.NoError(t, cli.config.set(targetFlag, "cloud"))
	require.NoError(t, cli.config.set(progressFlag, "true"))
	cli.isTerminal = func() bool { return true }
	cli.now = func() time.Time { return time.Unix(1700000000, 0) }
	p := cli.startDeploymentProgress("Deploying t1.a1.i1")
	require.NotNil(t, p)
	// Most tests render manually; the animation lifecycle is covered separately.
	p.stopAnimation()
	p.size = func() (int, int) { return 100, 30 }
	p.url = "https://console.example/run/42"
	t.Cleanup(p.close)
	return p, stderr
}

func provisioningSnapshot(t *testing.T) vespa.RunProgress {
	t.Helper()
	var snapshot vespa.RunProgress
	require.NoError(t, json.Unmarshal([]byte(`{"active":true,"status":"running","lastId":1,"steps":{"deployReal":{"status":"unfinished","startMillis":1699999900000},"installReal":{"status":"unfinished"},"copyVespaLogs":{"status":"unfinished"}},"log":{"deployReal":[{"at":1700000000000,"type":"info","message":"`+provisioningMessage+`"}]}}`), &snapshot))
	return snapshot
}

func TestProgressHostAndInstallationSnapshots(t *testing.T) {
	p, output := newProgressTest(t)
	snapshot := provisioningSnapshot(t)
	p.updateRun(42, snapshot)
	content := strings.Join(p.content(), "\n")
	assert.Contains(t, content, "Application hosts")
	assert.Contains(t, content, "1/3")
	assert.Contains(t, content, "Admin hosts")
	assert.Contains(t, content, "1/1")
	assert.Contains(t, content, "⠋ Running · 01:40")
	assert.Contains(t, content, "· Waiting")

	// Updated host counts can regress and totals can change.
	snapshot.Log["deployReal"][0].Message = strings.Replace(provisioningMessage, "1/3", "0/4", 1)
	p.updateRun(42, snapshot)
	assert.Contains(t, strings.Join(p.content(), "\n"), "0/4")
	// A heartbeat does not make old provisioning counts appear newly observed.
	snapshot.Log = nil
	p.cli.now = func() time.Time { return time.Unix(1700000060, 0) }
	p.updateRun(42, snapshot)
	assert.Contains(t, strings.Join(p.content(), "\n"), "last reported 01:00 ago")

	snapshot.Steps["deployReal"] = vespa.RunStep{Status: "succeeded"}
	snapshot.Steps["installReal"] = vespa.RunStep{Status: "unfinished", StartMillis: 1700000000000, Convergence: &vespa.RunConvergence{Nodes: 4, NeedPlatformUpgrade: 2, Services: 12, NeedNewConfig: 4, Booting: 1, NeedRestart: 1}}
	p.updateRun(42, snapshot)
	content = strings.Join(p.content(), "\n")
	assert.NotContains(t, content, "Application hosts")
	assert.Contains(t, content, "Service configuration")
	assert.Contains(t, content, "Platform upgrade")
	assert.Contains(t, content, "2/4")
	assert.Contains(t, content, "8/12")
	assert.Contains(t, content, "Nodes: 4 · booting: 1")
	assert.Contains(t, content, "need restart: 1")
	assert.Contains(t, output.String(), "\x1b[")

	// Replacement runs cannot inherit provisioning counts or activity.
	snapshot.Steps["deployReal"] = vespa.RunStep{Status: "unfinished"}
	p.updateRun(43, snapshot)
	assert.NotContains(t, strings.Join(p.content(), "\n"), "0/4")
}

func TestProgressBarLayout(t *testing.T) {
	p, _ := newProgressTest(t)
	run := progressRun{snapshot: vespa.RunProgress{Steps: map[string]vespa.RunStep{
		"installReal": {Status: "unfinished", Convergence: &vespa.RunConvergence{
			Nodes: 4, NeedPlatformUpgrade: 1, Services: 10, NeedNewConfig: 1,
		}},
	}}}
	for _, compact := range []bool{false, true} {
		indent := 0
		if compact {
			indent = 3
		}
		for _, width := range []int{99 + indent, 98 + indent, 40 + indent} {
			p.size = func() (int, int) { return width, 30 }
			var bars []string
			for _, line := range p.runLines(&run, compact, 36) {
				if strings.Contains(line, "Platform upgrade") || strings.Contains(line, "Service configuration") {
					bars = append(bars, line)
					assert.LessOrEqual(t, runewidth.StringWidth(line)+indent, width-1)
				}
			}
			if width == 99+indent {
				require.Len(t, bars, 1)
				assert.Contains(t, bars[0], "3/4")
				assert.Contains(t, bars[0], "9/10")
			} else {
				require.Len(t, bars, 2)
			}
		}
	}

	// Invalid or absent counters must not imply completion.
	run.snapshot.Steps["installReal"].Convergence.NeedPlatformUpgrade = 5
	run.snapshot.Steps["installReal"].Convergence.Services = 0
	content := strings.Join(p.runLines(&run, false, 36), "\n")
	assert.NotContains(t, content, "Platform upgrade")
	assert.NotContains(t, content, "Service configuration")
}

func TestProgressWarningsAndFailure(t *testing.T) {
	p, output := newProgressTest(t)
	snapshot := provisioningSnapshot(t)
	snapshot.Log["deployReal"] = append(snapshot.Log["deployReal"], vespa.RunLogEntry{At: 2, Type: "warning", Message: "Check the schema"})
	p.updateRun(42, snapshot)
	p.updateRun(42, snapshot)
	require.Len(t, p.warnings, 1)
	require.Len(t, p.recentWarnings, 1)
	assert.Contains(t, strings.Join(p.content(), "\n"), "Warning: Check the schema")
	snapshot.Active = false
	snapshot.Status = "deploymentFailed"
	p.updateRun(42, snapshot)
	assert.True(t, p.closed)
	assert.Contains(t, output.String(), "Deployment ended: deploymentFailed")
	p.close()
	assert.Nil(t, p.cli.progress)
}

func TestProgressTerminalSafetyAndBounds(t *testing.T) {
	assert.Equal(t, "safe red link", cleanProgressText("safe \x1b[31mred\x1b[0m \x1b]8;;https://evil.test\alink\x1b]8;;\a"))
	assert.Equal(t, "a b c", cleanProgressText("a\nb\rc"))
	assert.NotContains(t, cleanProgressText("a\u009bb\u202ec"), "\u009b")
	p, output := newProgressTest(t)
	p.size = func() (int, int) { return 40, 8 }
	p.updateRun(42, provisioningSnapshot(t))
	frame := output.String()
	assert.LessOrEqual(t, strings.Count(frame, "\n"), 7)
	for _, line := range strings.Split(frame, "\n") {
		assert.LessOrEqual(t, runewidth.StringWidth(cleanProgressText(line)), 39)
	}
	assert.Contains(t, frame, "detail lines hidden")
	assert.Contains(t, frame, "Console:")
	assert.Contains(t, progressBar("Application hosts", 1, 3, 38), "1/3")
	assert.LessOrEqual(t, runewidth.StringWidth(progressBar("Application hosts", 1, 3, 38)), 38)
	p.finish("Monitoring stopped; deployment may still be running")
	assert.True(t, p.closed)
	// Tiny terminals must not panic or exceed their available rows.
	p.closed = false
	p.size = func() (int, int) { return 1, 2 }
	require.NotPanics(t, p.render)
}

func TestProgressColors(t *testing.T) {
	p, output := newProgressTest(t)
	previous := color.NoColor
	t.Cleanup(func() { color.NoColor = previous })
	color.NoColor = false
	p.updateRun(42, provisioningSnapshot(t))
	assert.Contains(t, output.String(), "\x1b[36mApplication hosts")
	assert.Contains(t, output.String(), "\x1b[35mAdmin hosts")
	snapshot := provisioningSnapshot(t)
	snapshot.Steps["deployReal"] = vespa.RunStep{Status: "succeeded"}
	snapshot.Steps["installReal"] = vespa.RunStep{Status: "unfinished", Convergence: &vespa.RunConvergence{
		Nodes: 4, NeedPlatformUpgrade: 1, Services: 10, NeedNewConfig: 1,
	}}
	output.Reset()
	p.updateRun(42, snapshot)
	assert.Contains(t, output.String(), "\x1b[34mPlatform upgrade")
	assert.Contains(t, output.String(), "\x1b[32mService configuration")
	assert.Contains(t, output.String(), progressBarSeparator)
	color.NoColor = true
	output.Reset()
	p.lines = 0
	p.render()
	assert.NotContains(t, output.String(), "\x1b[36m")
	assert.NotContains(t, output.String(), "\x1b[34m")
	assert.NotContains(t, output.String(), "\x1b[32m")
}

func TestProgressProductionIdentityAndMissingDetails(t *testing.T) {
	p, _ := newProgressTest(t)
	snapshot := provisioningSnapshot(t)
	jobs := []vespa.BuildJobProgress{
		{Instance: "one", JobName: "system-test", RunID: 1, RunStatus: "running", Details: &snapshot},
		{Instance: "two", JobName: "system-test", RunID: 1, RunStatus: "running", DetailsUnavailable: true},
	}
	p.updateBuild(vespa.BuildProgress{Jobs: jobs})
	content := strings.Join(p.content(), "\n")
	assert.Contains(t, content, "one / system-test")
	assert.Contains(t, content, "two / system-test")
	assert.Contains(t, content, "Details unavailable")
	require.Len(t, p.runs, 1)
	jobs[0].RunID = 2
	jobs[0].Details = nil
	p.updateBuild(vespa.BuildProgress{Jobs: jobs})
	assert.NotContains(t, strings.Join(p.content(), "\n"), "Application hosts")
	p.updateBuild(vespa.BuildProgress{})
	assert.Contains(t, strings.Join(p.content(), "\n"), "Waiting for pipeline jobs")
}

func TestProgressParallelRunLayout(t *testing.T) {
	p, output := newProgressTest(t)
	snapshot := provisioningSnapshot(t)
	snapshot.Steps["installTester"] = vespa.RunStep{Status: "unfinished", StartMillis: 1700000000000}
	jobs := []vespa.BuildJobProgress{
		{Instance: "default", JobName: "system-test", RunID: 1, RunStatus: "running", Details: &snapshot},
		{Instance: "東京", JobName: "staging-test", RunID: 2, RunStatus: "running", Details: &snapshot},
		{Instance: "default", JobName: "production-aws-us-east-1c", RunID: 3, RunStatus: "running", Details: &snapshot},
	}
	for _, width := range []int{120, 60, 40} {
		p.size = func() (int, int) { return width, 40 }
		output.Reset()
		p.updateBuild(vespa.BuildProgress{Jobs: jobs})
		lines := p.content()
		columns := map[int]bool{}
		headers, statuses := 0, 0
		for i, line := range lines {
			if strings.HasPrefix(line, "── ") {
				headers++
				require.Greater(t, i, 0)
				assert.Empty(t, lines[i-1], "separate job sections")
			}
			if column := strings.Index(line, " Running"); column >= 0 {
				statuses++
				columns[runewidth.StringWidth(line[:column])] = true
				assert.LessOrEqual(t, runewidth.StringWidth(line), width-1)
			}
			if strings.HasPrefix(line, "Console:") {
				require.Greater(t, i, 0)
				assert.Empty(t, lines[i-1], "separate the Console link from job details")
			}
		}
		assert.Equal(t, 3, headers)
		assert.Equal(t, 9, statuses)
		assert.Len(t, columns, 1, "headers and step statuses must align by terminal columns")
		for _, line := range strings.Split(output.String(), "\n") {
			assert.LessOrEqual(t, runewidth.StringWidth(cleanProgressText(line)), width-1)
		}
	}
	p.finish("✓ Deployed to production")
	assert.Contains(t, output.String(), "\n\nConsole: "+p.url+"\n")
}

func TestProgressEligibility(t *testing.T) {
	for _, scenario := range []string{"default", "redirected", "quiet", "dumb", "screwdriver", "local", "override"} {
		t.Run(scenario, func(t *testing.T) {
			cli, _, _ := newTestCLI(t)
			require.NoError(t, cli.config.set(targetFlag, "cloud"))
			require.NoError(t, cli.config.set(progressFlag, "true"))
			cli.isTerminal = func() bool { return true }
			switch scenario {
			case "default":
				require.NoError(t, cli.config.unset(progressFlag))
			case "redirected":
				cli.isTerminal = func() bool { return false }
			case "quiet":
				require.NoError(t, cli.config.set(quietFlag, "true"))
			case "dumb":
				cli.Environment["TERM"] = "dumb"
			case "screwdriver":
				cli.Environment["SCREWDRIVER"] = "true"
			case "local":
				require.NoError(t, cli.config.set(targetFlag, "local"))
			case "override":
				require.NoError(t, cli.cmd.PersistentFlags().Set(progressFlag, "false"))
			}
			assert.Nil(t, cli.startDeploymentProgress("test"))
		})
	}
}

func TestProgressAnimationBetweenPolls(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		cli, _, output := newTestCLI(t)
		require.NoError(t, cli.config.set(targetFlag, "cloud"))
		require.NoError(t, cli.config.set(progressFlag, "true"))
		cli.isTerminal = func() bool { return true }
		p := cli.startDeploymentProgress("Deploying application")
		require.NotNil(t, p)
		defer p.close()
		takeOutput := func() string {
			p.mu.Lock()
			defer p.mu.Unlock()
			text := output.String()
			output.Reset()
			return text
		}
		synctest.Wait()
		assert.Empty(t, takeOutput(), "authentication prompts must not trigger redraws")

		snapshot := provisioningSnapshot(t)
		snapshot.Steps["deployReal"] = vespa.RunStep{Status: "unfinished", StartMillis: time.Now().Add(-time.Second).UnixMilli()}
		p.updateRun(42, snapshot)
		synctest.Wait()
		assert.Contains(t, takeOutput(), "⠋ Running")

		// No further API updates: the cached snapshot still animates and keeps time.
		time.Sleep(100 * time.Millisecond)
		synctest.Wait()
		frame := takeOutput()
		assert.Contains(t, frame, "⠙ Running")
		assert.Contains(t, frame, "1/3")
		time.Sleep(900 * time.Millisecond)
		synctest.Wait()
		frame = takeOutput()
		assert.Contains(t, frame, "elapsed 00:01")
		assert.Contains(t, frame, "Running · 00:02")
		time.Sleep(time.Second)
		synctest.Wait()
		frame = takeOutput()
		assert.Contains(t, frame, "elapsed 00:02")
		assert.Contains(t, frame, "Running · 00:03")
		assert.Empty(t, cli.httpClient.(*mock.HTTPClient).Requests)

		p.finish("✓ Deployment complete")
		assert.Contains(t, takeOutput(), "✓ Deployment complete")
		time.Sleep(time.Second)
		synctest.Wait()
		assert.Empty(t, takeOutput(), "redraws must stop before diagnostics or final output")
	})
}
