// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Production tests: metric presets checked against Grafana metrics for a canary production deployment.
// See https://docs.vespa.ai/en/reference/applications/testing-production.html
package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/fatih/color"
	"gopkg.in/yaml.v3"
)

// metricPresets are the named metric presets a production test may check.
// Keep in sync with metric-presets.json in the controller (the authoritative source),
// which is documented at https://docs.vespa.ai/en/reference/applications/testing-production.html#metric-presets
var metricPresets = map[string]struct{}{
	// Overview
	"buckets-out-of-sync":                             {},
	"container-nodes-down":                            {},
	"container-thread-saturation-document-api-only":   {},
	"container-thread-saturation-search-document-api": {},
	"container-thread-saturation-search-only":         {},
	"content-executor-saturation":                     {},
	"content-groups-nodes-down":                       {},
	"core-dumps":                                      {},
	"disk-utilization-container":                      {},
	"disk-utilization-content":                        {},
	"documents-per-content-cluster-max":               {},
	"feed-blocked":                                    {},
	"feed-latency-max":                                {},
	"headroom-to-feed-block-per-content-cluster":      {},
	"http-2xx-responses-sum":                          {},
	"http-4xx-responses-sum":                          {},
	"http-5xx-responses-sum":                          {},
	"http-read-latency-avg":                           {},
	"http-read-latency-max":                           {},
	"http-read-latency-p95":                           {},
	"http-read-latency-p99":                           {},
	"http-write-latency-max":                          {},
	"jvm-heap-pressure":                               {},
	"memory-utilization-node-container":               {},
	"memory-utilization-node-content":                 {},
	"nodes-alive-min":                                 {},
	"qos-read-success":                                {},
	"qos-write-success":                               {},
	"query-latency-avg":                               {},
	"query-latency-max":                               {},
	"restarts":                                        {},
	// Query
	"degraded-queries-sum":                     {},
	"docs-matched-per-query-per-rank-profile":  {},
	"docsum-executor-accepted-rate":            {},
	"docsum-executor-queue-size-max":           {},
	"docsum-latency-avg":                       {},
	"docsum-latency-max":                       {},
	"document-summaries-requested-rate":        {},
	"documents-coverage":                       {},
	"documents-matched-rate":                   {},
	"empty-results-sum":                        {},
	"error-rate-pct":                           {},
	"failed-queries-sum":                       {},
	"grouping-time-per-rank-profile-avg":       {},
	"grouping-time-per-rank-profile-peak":      {},
	"hits-per-query-avg":                       {},
	"match-executor-accepted-rate":             {},
	"match-executor-queue-size-max":            {},
	"match-executor-utilization-avg":           {},
	"match-executor-utilization-max":           {},
	"matching-queries-rate":                    {},
	"matching-query-latency-avg":               {},
	"matching-query-setup-time-avg":            {},
	"queries-per-second-by-content-group-min":  {},
	"queries-per-second-per-rank-profile":      {},
	"query-container-latency-avg":              {},
	"query-error-breakdown-backend-comm":       {},
	"query-error-breakdown-empty-docsums":      {},
	"query-error-breakdown-invalid-param":      {},
	"query-error-breakdown-timeout":            {},
	"query-error-breakdown-unhandled":          {},
	"query-latency-p95":                        {},
	"query-latency-p99":                        {},
	"query-rate-qps-avg":                       {},
	"query-rate-qps-peak":                      {},
	"query-timeout-p99":                        {},
	"rank-profile-query-latency-max":           {},
	"rerank-time-per-rank-profile-avg":         {},
	"rerank-time-per-rank-profile-peak":        {},
	"search-handler-utilization-avg":           {},
	"search-handler-utilization-max":           {},
	"search-protocol-query-latency-avg":        {},
	"search-protocol-query-latency-max":        {},
	"soft-doom-factor-per-rank-profile-avg":    {},
	"soft-doom-factor-per-rank-profile-max":    {},
	"soft-doom-factor-per-rank-profile-min":    {},
	"soft-doomed-queries-per-rank-profile-sum": {},
	// Feed
	"container-feed-latency-max":                      {},
	"container-feed-operations-sum":                   {},
	"content-commit-latency-avg":                      {},
	"content-commit-operations-sum":                   {},
	"content-storage-feed-rates-put":                  {},
	"content-storage-feed-rates-remove":               {},
	"content-storage-feed-rates-update":               {},
	"content-storage-put-latency-max":                 {},
	"content-storage-update-latency-avg":              {},
	"content-storage-update-latency-max":              {},
	"distributor-latency-gets-max":                    {},
	"distributor-latency-puts-max":                    {},
	"distributor-latency-removes-max":                 {},
	"distributor-latency-updates-max":                 {},
	"distributor-latency-visitor-max":                 {},
	"distributor-operation-gets-failures-sum":         {},
	"distributor-operation-puts-failures-sum":         {},
	"distributor-operation-rates-gets-ok-sum":         {},
	"distributor-operation-rates-puts-ok-sum":         {},
	"distributor-operation-rates-removes-ok-sum":      {},
	"distributor-operation-rates-updates-ok-sum":      {},
	"distributor-operation-rates-visitor-ok-sum":      {},
	"distributor-operation-removes-failures-sum":      {},
	"distributor-operation-updates-failures-sum":      {},
	"distributor-operation-visitor-failures-sum":      {},
	"document-processing-latency-avg":                 {},
	"document-processing-rate-sum":                    {},
	"feed-blocked-nodes-above-resource-limit":         {},
	"feed-latency-avg-ms":                             {},
	"http-api-feed-rates-put":                         {},
	"http-api-feed-rates-remove":                      {},
	"http-api-feed-rates-update":                      {},
	"http-api-latency-avg":                            {},
	"http-api-pending-requests-max":                   {},
	"http-api-success-vs-failures-failed":             {},
	"http-api-success-vs-failures-parse-error":        {},
	"http-api-success-vs-failures-success":            {},
	"http-write-latency-avg":                          {},
	"http-write-latency-p95":                          {},
	"http-write-latency-p99":                          {},
	"memory-index-docs-in-memory-max":                 {},
	"persistence-engine-input-queue-avg":              {},
	"persistence-engine-input-queue-max":              {},
	"persistence-engine-throttle-saturation-active":   {},
	"persistence-engine-throttle-saturation-throttle": {},
	// Content Node
	"attribute-feeding-blocked-pct":             {},
	"bucket-move-pending-max":                   {},
	"document-store-cache-hit-rate-max":         {},
	"document-store-disk-usage-max":             {},
	"documents-count-active":                    {},
	"documents-count-ready":                     {},
	"documents-count-removed":                   {},
	"documents-count-total":                     {},
	"field-writer-saturation-max":               {},
	"field-writer-utilization-avg":              {},
	"filestor-average-queue-wait-avg":           {},
	"filestor-queue-size-max":                   {},
	"maintenance-job-activity-attr-flush":       {},
	"maintenance-job-activity-disk-idx-fusion":  {},
	"maintenance-job-activity-docstore-compact": {},
	"maintenance-job-activity-lid-compact":      {},
	"maintenance-job-activity-mem-idx-flush":    {},
	"proton-disk-usage-avg":                     {},
	"proton-executor-utilization-avg":           {},
	"proton-executor-utilization-max":           {},
	"proton-memory-usage-avg":                   {},
	"shared-executor-queue-size-max":            {},
	"shared-executor-utilization-avg":           {},
	"shared-executor-utilization-max":           {},
	// Resources
	"cpu-iowait-avg":                                    {},
	"cpu-iowait-max":                                    {},
	"cpu-iowait-min":                                    {},
	"cpu-throttled-time-sum":                            {},
	"cpu-utilization-container":                         {},
	"cpu-utilization-content":                           {},
	"default-handler-common-utilization-avg":            {},
	"default-handler-common-utilization-max":            {},
	"default-handler-common-work-queue-size-avg":        {},
	"default-handler-common-work-queue-size-max":        {},
	"default-handler-common-work-queue-utilization-avg": {},
	"default-handler-common-work-queue-utilization-max": {},
	"disk-utilization-avg":                              {},
	"disk-utilization-max":                              {},
	"disk-utilization-min":                              {},
	"feedapi-handler-utilization-avg":                   {},
	"feedapi-handler-utilization-max":                   {},
	"feedapi-handler-work-queue-size-avg":               {},
	"feedapi-handler-work-queue-size-max":               {},
	"feedapi-handler-work-queue-utilization-avg":        {},
	"feedapi-handler-work-queue-utilization-max":        {},
	"gpu-memory-utilization-max":                        {},
	"gpu-utilization-container-max":                     {},
	"jvm-direct-memory-capacity":                        {},
	"jvm-direct-memory-used":                            {},
	"jvm-gc-overhead-max":                               {},
	"jvm-gc-pause-duration-avg":                         {},
	"jvm-gc-pause-duration-max":                         {},
	"jvm-heap-usage-capacity":                           {},
	"jvm-heap-usage-used":                               {},
	"jvm-native-memory-avg":                             {},
	"memory-node-utilization-avg":                       {},
	"memory-node-utilization-max":                       {},
	"memory-node-utilization-min":                       {},
	"memory-utilization-content":                        {},
	"network-throughput-bytes-received-sent-received":   {},
	"network-throughput-bytes-received-sent-sent":       {},
	"node-liveness-min":                                 {},
	"open-server-connections-max":                       {},
	"requests-per-http-connection-avg":                  {},
	"requests-per-http-connection-max":                  {},
	"search-handler-work-queue-size-avg":                {},
	"search-handler-work-queue-size-max":                {},
	"search-handler-work-queue-utilization-avg":         {},
	"search-handler-work-queue-utilization-max":         {},
	// Autoscaling: Container
	"active-nodes-container":                   {},
	"non-active-node-fraction-container":       {},
	"query-growth-rate-vs-3h-average":          {},
	"query-vs-write-rate-cpu-mix-driver-query": {},
	"query-vs-write-rate-cpu-mix-driver-write": {},
	// Autoscaling: Content
	"active-nodes-count":                               {},
	"active-nodes-non-active-fraction":                 {},
	"autoscaling-actions-sum":                          {},
	"cpu-load-adjustment-ratio":                        {},
	"cpu-load-peak-vs-ideal-ideal":                     {},
	"cpu-load-peak-vs-ideal-peak":                      {},
	"query-growth-rate-vs-3h-average-content":          {},
	"query-vs-write-rate-cpu-mix-driver-query-content": {},
	"query-vs-write-rate-cpu-mix-driver-write-content": {},
	// Nearest Neighbor Search
	"ann-timeout-rate-sum":                    {},
	"approximate-nns-distances-computed-rate": {},
	"approximate-nns-nodes-visited-rate":      {},
	"approximate-nns-query-rate":              {},
	"approximate-nns-time-avg":                {},
	"approximate-nns-time-max":                {},
	"approximate-nns-visit-efficiency":        {},
	"buckets-pending-merge-sum":               {},
	"documents-active-total":                  {},
	"documents-ready-total":                   {},
	"exact-nns-distance-ratio-pct":            {},
	"exact-nns-distances-computed-rate":       {},
	"nns-query-latency-avg":                   {},
	"nns-query-latency-max":                   {},
	"non-approximate-nns-query-rate":          {},
	"query-matching-time-avg":                 {},
	"query-matching-time-max":                 {},
	"query-rate":                              {},
	"query-setup-time-avg":                    {},
	"query-setup-time-excl-ann-avg":           {},
	"query-setup-time-max":                    {},
	"soft-timeout-rate-sum":                   {},
	"thread-pool-match-utilization-avg":       {},
	"thread-pool-match-utilization-max":       {},
	"total-distances-computed-sum":            {},
	// Health
	"cluster-state-changes-sum":                    {},
	"core-dumps-processed-max":                     {},
	"deactivated-containers":                       {},
	"estimated-time-to-in-sync":                    {},
	"failed-component-graphs":                      {},
	"http-requests-prematurely-closed":             {},
	"merge-bucket-pending":                         {},
	"node-count-max":                               {},
	"node-events-sum":                              {},
	"node-state-down-max":                          {},
	"node-state-initializing-max":                  {},
	"node-state-maintenance-max":                   {},
	"node-state-retired-max":                       {},
	"node-state-up-max":                            {},
	"re-indexing-remaining":                        {},
	"resource-usage-attribute-address-space":       {},
	"resource-usage-max-disk-utilization-vs-limit": {},
	"service-restarts-total":                       {},
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
