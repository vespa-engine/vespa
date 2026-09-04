// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Production tests: metric presets checked against Grafana metrics for a canary production deployment.
// See https://docs.vespa.ai/en/reference/applications/testing-production.html
package cmd

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/fatih/color"
	"gopkg.in/yaml.v3"
)


// Fetch all preset names. Documented at:
// https://docs.vespa.ai/en/reference/applications/testing-production.html#metric-presets.
//
//go:embed metric-presets.json
var metricPresetsJSON []byte

var metricPresets = loadMetricPresets(metricPresetsJSON)

func loadMetricPresets(data []byte) map[string]struct{} {
	var names []string
	if err := json.Unmarshal(data, &names); err != nil {
		panic(fmt.Sprintf("invalid embedded metric-presets.json: %v", err))
	}
	presets := make(map[string]struct{}, len(names))
	for _, name := range names {
		presets[name] = struct{}{}
	}
	return presets
}

// metricTest is a single named production test, checking a metric preset against a min/max bound over
// a time window.
type metricTest struct {
	Name   string   `json:"name" yaml:"name"`
	Metric string   `json:"metric" yaml:"metric"`
	Time   *int     `json:"time" yaml:"time"`
	Min    *float64 `json:"min" yaml:"min"`
	Max    *float64 `json:"max" yaml:"max"`
}

type metricTestFile struct {
	Tests []metricTest `json:"tests" yaml:"tests"`
}

func isMetricTestFile(path string) (bool, error) {
	ext := filepath.Ext(path)
	if ext == ".yaml" || ext == ".yml" {
		return true, nil
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return false, errHint(err, "See https://docs.vespa.ai/en/reference/applications/testing-production.html")
	}
	var probe struct {
		Steps  json.RawMessage `json:"steps"`
		Metric json.RawMessage `json:"metric"`
		Tests  json.RawMessage `json:"tests"`
	}
	if err := json.Unmarshal(data, &probe); err != nil {
		return false, errHint(fmt.Errorf("failed parsing test at %s: %w", path, err),
			"See https://docs.vespa.ai/en/reference/applications/testing.html")
	}
	if probe.Steps != nil {
		return false, nil
	}
	if probe.Metric != nil || probe.Tests != nil {
		return true, nil
	}
	return false, errHint(fmt.Errorf("could not determine test type of %s", path),
		"A production test must have either a 'steps' field, or a 'metric' or 'tests' field",
		"See https://docs.vespa.ai/en/reference/applications/testing-production.html")
}

func parseMetricTests(data []byte, ext string) ([]metricTest, error) {
	unmarshal := json.Unmarshal
	if ext == ".yaml" || ext == ".yml" {
		unmarshal = yaml.Unmarshal
	}
	var file metricTestFile
	if err := unmarshal(data, &file); err != nil {
		return nil, err
	}
	if len(file.Tests) > 0 {
		return file.Tests, nil
	}
	var single metricTest
	if err := unmarshal(data, &single); err != nil {
		return nil, err
	}
	if single.Metric == "" {
		return nil, fmt.Errorf("found no tests: expected a single test object, or an object with a 'tests' field")
	}
	return []metricTest{single}, nil
}

func validateMetricTest(t metricTest) error {
	if t.Metric == "" {
		return fmt.Errorf("missing required field 'metric'")
	}
	if _, ok := metricPresets[t.Metric]; !ok {
		return fmt.Errorf("'%s' is not a known metric preset", t.Metric)
	}
	if t.Time == nil {
		return fmt.Errorf("missing required field 'time'")
	}
	if *t.Time <= 0 {
		return fmt.Errorf("field 'time' must be a positive whole number, got %d", *t.Time)
	}
	if t.Min == nil && t.Max == nil {
		return fmt.Errorf("at least one of 'min' and 'max' is required")
	}
	return nil
}

func runMetricTestFile(cli *CLI, testPath string, dryRun bool) (int, error) {
	data, err := os.ReadFile(testPath)
	if err != nil {
		return 0, errHint(err, "See https://docs.vespa.ai/en/reference/applications/testing-production.html")
	}
	tests, err := parseMetricTests(data, filepath.Ext(testPath))
	if err != nil {
		return 0, errHint(fmt.Errorf("failed parsing test at %s: %w", testPath, err),
			"See https://docs.vespa.ai/en/reference/applications/testing-production.html")
	}
	for _, t := range tests {
		name := t.Name
		if name == "" {
			name = "<unnamed test>"
		}
		if err := validateMetricTest(t); err != nil {
			return 0, errHint(fmt.Errorf("invalid test '%s' in %s: %w", name, testPath, err),
				"See https://docs.vespa.ai/en/reference/applications/testing-production.html")
		}
		if !dryRun {
			fmt.Fprintf(cli.Stdout, "%s: %s\n", name, color.GreenString("OK (evaluated against live metrics after deployment)"))
		}
	}
	return len(tests), nil
}

// runTestFile runs, or for metric tests validates, the single test file at testPath. It returns the
// number of tests found, and the failure message of a failed legacy test, if any.
func runTestFile(cli *CLI, testPath string, isProductionSuite bool, context testContext, waiter *Waiter) (int, string, error) {
	if isProductionSuite {
		isMetric, err := isMetricTestFile(testPath)
		if err != nil {
			return 0, "", err
		}
		if isMetric {
			n, err := runMetricTestFile(cli, testPath, context.dryRun)
			return n, "", err
		}
	}
	failure, err := runTest(testPath, context, waiter)
	return 1, failure, err
}
