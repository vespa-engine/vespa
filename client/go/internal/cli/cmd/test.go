// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa test command
// Author: jonmv

package cmd

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newTestCmd(cli *CLI) *cobra.Command {
	var waitSecs int
	testCmd := &cobra.Command{
		Use:   "test test-directory-or-file",
		Short: "Run a test suite, or a single test",
		Long: `Run a test suite, or a single test

Runs all JSON test files in the specified directory, or the single JSON test file specified.

See https://docs.vespa.ai/en/reference/applications/testing.html for details.`,
		Example: `$ vespa test src/test/application/tests/system-test
$ vespa test src/test/application/tests/system-test/feed-and-query.json`,
		Args:              cobra.ExactArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			count, failed, err := runTests(cli, args[0], false, waiter)
			if err != nil {
				return err
			}
			if len(failed) != 0 {
				plural := "s"
				if count == 1 {
					plural = ""
				}
				fmt.Fprintf(cli.Stdout, "\n%s %d of %d test%s failed:\n", color.RedString("Failure:"), len(failed), count, plural)
				for _, test := range failed {
					fmt.Fprintln(cli.Stdout, test)
				}
				return ErrCLI{Status: 3, error: fmt.Errorf("tests failed"), quiet: true}
			} else {
				plural := "s"
				if count == 1 {
					plural = ""
				}
				fmt.Fprintf(cli.Stdout, "\n%s %d test%s OK\n", color.GreenString("Success:"), count, plural)
				return nil
			}
		},
	}
	cli.bindWaitFlag(testCmd, 0, &waitSecs)
	return testCmd
}

func runTests(cli *CLI, rootPath string, dryRun bool, waiter *Waiter) (int, []string, error) {
	count := 0
	failed := make([]string, 0)
	if stat, err := os.Stat(rootPath); err != nil {
		return 0, nil, errHint(err, "See https://docs.vespa.ai/en/reference/applications/testing.html")
	} else if stat.IsDir() {
		tests, err := os.ReadDir(rootPath)
		if err != nil {
			return 0, nil, errHint(err, "See https://docs.vespa.ai/en/reference/applications/testing.html")
		}
		context := testContext{testsPath: rootPath, dryRun: dryRun, cli: cli, authMethod: cli.selectAuthMethod(), clusters: map[string]*vespa.Service{}}
		previousFailed := false
		for _, test := range tests {
			if !test.IsDir() && filepath.Ext(test.Name()) == ".json" {
				testPath := filepath.Join(rootPath, test.Name())
				if previousFailed {
					fmt.Fprintln(cli.Stdout, "")
					previousFailed = false
				}
				failure, err := runTest(testPath, context, waiter)
				if err != nil {
					return 0, nil, err
				}
				if failure != "" {
					failed = append(failed, failure)
					previousFailed = true
				}
				count++
			}
		}
	} else if strings.HasSuffix(stat.Name(), ".json") {
		failure, err := runTest(rootPath, testContext{testsPath: filepath.Dir(rootPath), dryRun: dryRun, cli: cli, authMethod: cli.selectAuthMethod(), clusters: map[string]*vespa.Service{}}, waiter)
		if err != nil {
			return 0, nil, err
		}
		if failure != "" {
			failed = append(failed, failure)
		}
		count++
	}
	if count == 0 {
		return 0, nil, errHint(fmt.Errorf("failed to find any tests at %s", rootPath), "See https://docs.vespa.ai/en/reference/applications/testing.html")
	}
	return count, failed, nil
}

// Runs the test at the given path, and returns the specified test name if the test fails
func runTest(testPath string, context testContext, waiter *Waiter) (string, error) {
	var test test
	testBytes, err := os.ReadFile(testPath)
	if err != nil {
		return "", errHint(err, "See https://docs.vespa.ai/en/reference/applications/testing.html")
	}
	if err = json.Unmarshal(testBytes, &test); err != nil {
		return "", errHint(fmt.Errorf("failed parsing test at %s: %w", testPath, err), "See https://docs.vespa.ai/en/reference/applications/testing.html")
	}

	testName := test.Name
	if test.Name == "" {
		testName = filepath.Base(testPath)
	}
	if !context.dryRun {
		fmt.Fprintf(context.cli.Stdout, "%s:", testName)
	}

	defaultParameters, err := getParameters(test.Defaults.ParametersRaw, filepath.Dir(testPath))
	if err != nil {
		fmt.Fprintln(context.cli.Stderr)
		return "", errHint(fmt.Errorf("invalid default parameters for %s: %w", testName, err), "See https://docs.vespa.ai/en/reference/applications/testing.html")
	}

	if len(test.Steps) == 0 {
		fmt.Fprintln(context.cli.Stderr)
		return "", errHint(fmt.Errorf("a test must have at least one step, but none were found in %s", testPath), "See https://docs.vespa.ai/en/reference/applications/testing.html")
	}
	seen := make(seenClusters)
	for i, step := range test.Steps {
		seen.warmup(step, test.Defaults.Cluster, defaultParameters, context, waiter)
		stepName := fmt.Sprintf("Step %d", i+1)
		if step.Name != "" {
			stepName += ": " + step.Name
		}
		failure, longFailure, err := verify(step, test.Defaults.Cluster, defaultParameters, context, waiter)
		if err != nil {
			fmt.Fprintln(context.cli.Stderr)
			return "", errHint(fmt.Errorf("error in %s: %w", stepName, err), "See https://docs.vespa.ai/en/reference/applications/testing.html")
		}
		if !context.dryRun {
			if failure != "" {
				fmt.Fprintf(context.cli.Stdout, " %s\n%s:\n%s\n", color.RedString("failed"), stepName, longFailure)
				return fmt.Sprintf("%s: %s: %s", testName, stepName, failure), nil
			}
			if i == 0 {
				fmt.Fprintf(context.cli.Stdout, " ")
			}
			fmt.Fprint(context.cli.Stdout, ".")
		}
	}
	if !context.dryRun {
		fmt.Fprintln(context.cli.Stdout, color.GreenString(" OK"))
	}
	return "", nil
}

// Asserts specified response is obtained for request, or returns a failure message, or an error if this fails
func verify(step step, defaultCluster string, defaultParameters map[string]string, context testContext, waiter *Waiter) (string, string, error) {
	requestBody, err := getBody(step.Request.BodyRaw, context.testsPath)
	if err != nil {
		return "", "", err
	}

	parameters, err := getParameters(step.Request.ParametersRaw, context.testsPath)
	if err != nil {
		return "", "", err
	}
	for name, value := range defaultParameters {
		if _, present := parameters[name]; !present {
			parameters[name] = value
		}
	}

	cluster := step.Request.Cluster
	if cluster == "" {
		cluster = defaultCluster
	}

	method := step.Request.Method
	if method == "" {
		method = "GET"
	}

	header := http.Header{}
	for k, v := range step.Request.Headers {
		header.Set(k, v)
	}
	if header.Get("Content-Type") == "" { // Set default if not specified by test
		header.Set("Content-Type", "application/json")
	}
	if context.authMethod == "token" {
		if err := context.cli.addBearerToken(&header); err != nil {
			return "", "", err
		}
	}

	var service *vespa.Service
	requestUri := step.Request.URI
	if requestUri == "" {
		requestUri = "/search/"
	}
	requestUrl, err := url.ParseRequestURI(requestUri)
	if err != nil {
		return "", "", err
	}
	externalEndpoint := requestUrl.IsAbs()
	if !externalEndpoint && filepath.Base(context.testsPath) == "production-test" {
		return "", "", fmt.Errorf("production tests may not specify requests against Vespa endpoints")
	}
	if !externalEndpoint && !context.dryRun {
		target, err := context.target()
		if err != nil {
			return "", "", err
		}
		ok := false
		service, ok = context.clusters[cluster]
		if !ok && waiter != nil {
			// Cache service so we don't have to discover it for every step
			service, err = waiter.ServiceWithAuthMethod(target, cluster, context.authMethod)
			if err != nil {
				return "", "", err
			}
			if context.authMethod == "token" {
				service.TLSOptions.CertificateFile = ""
				service.TLSOptions.PrivateKeyFile = ""
			}
			context.clusters[cluster] = service
		}
		fullURL := joinURL(service.BaseURL, requestUri)
		requestUrl, err = url.ParseRequestURI(fullURL)
		if err != nil {
			return "", "", err
		}
	}
	query := requestUrl.Query()
	for name, value := range parameters {
		query.Add(name, value)
	}
	requestUrl.RawQuery = query.Encode()

	request := &http.Request{
		URL:    requestUrl,
		Method: method,
		Header: header,
		Body:   io.NopCloser(bytes.NewReader(requestBody)),
	}
	defer request.Body.Close()

	statusCode := step.Response.Code
	if statusCode == 0 {
		statusCode = 200
	}

	responseBodySpecBytes, err := getBody(step.Response.BodyRaw, context.testsPath)
	if err != nil {
		return "", "", err
	}
	var responseBodySpec interface{}
	if responseBodySpecBytes != nil {
		err = json.Unmarshal(responseBodySpecBytes, &responseBodySpec)
		if err != nil {
			return "", "", fmt.Errorf("invalid response body spec: %w", err)
		}
	}

	if context.dryRun {
		return "", "", nil
	}

	var response *http.Response
	if externalEndpoint {
		httputil.ConfigureTLS(context.cli.httpClient, []tls.Certificate{}, nil, false)
		response, err = context.cli.httpClient.Do(request, 60*time.Second)
	} else {
		response, err = service.Do(request, 600*time.Second) // Vespa should provide a response within the given request timeout
	}
	if err != nil {
		return "", "", err
	}
	defer response.Body.Close()

	if statusCode != response.StatusCode {
		hint := ""
		if response.StatusCode == 403 && context.authMethod == "token" {
			hint = "\nHint: Make sure the VESPA_CLI_DATA_PLANE_TOKEN environment variable is set to a valid token"
		}
		return fmt.Sprintf("Unexpected status code: %s", color.RedString(strconv.Itoa(response.StatusCode))),
			fmt.Sprintf("Unexpected status code\nExpected: %s\nActual:   %s\nRequested: %s at %s\nResponse:\n%s%s",
				color.CyanString(strconv.Itoa(statusCode)),
				color.RedString(strconv.Itoa(response.StatusCode)),
				color.CyanString(method),
				color.CyanString(requestUrl.String()),
				ioutil.ReaderToJSON(response.Body),
				hint), nil
	}

	if responseBodySpec == nil {
		return "", "", nil
	}

	responseBodyBytes, err := io.ReadAll(response.Body)
	if err != nil {
		return "", "", err
	}
	var responseBody interface{}
	err = json.Unmarshal(responseBodyBytes, &responseBody)
	if err != nil {
		return "", "", fmt.Errorf("got non-JSON response; %w:\n%s", err, string(responseBodyBytes))
	}

	failure, expected, actual, err := compare(responseBodySpec, responseBody, "")
	if failure != "" {
		responsePretty, _ := json.MarshalIndent(responseBody, "", "  ")
		longFailure := failure
		if expected != "" {
			longFailure += "\nExpected: " + expected
		}
		if actual != "" {
			failure += ": " + actual
			longFailure += "\nActual:   " + actual
		}
		longFailure += fmt.Sprintf("\nRequested: %s at %s\nResponse:\n%s", color.CyanString(method), color.CyanString(requestUrl.String()), string(responsePretty))
		return failure, longFailure, err
	}
	return "", "", err
}

func compare(expected interface{}, actual interface{}, path string) (string, string, string, error) {
	typeMatch := false
	valueMatch := false
	switch u := expected.(type) {
	case nil:
		typeMatch = actual == nil
		valueMatch = actual == nil
	case bool:
		v, ok := actual.(bool)
		typeMatch = ok
		valueMatch = ok && u == v
	case float64:
		v, ok := actual.(float64)
		typeMatch = ok
		if ok {
			absDiff := math.Abs(u - v)
			// Allow match if absolute difference is small
			valueMatch = absDiff < 1e-9
			// Or if relative difference is less than 4 ULP (4 * machine epsilon)
			if !valueMatch && u != 0 {
				ulpSlack := math.Abs(u) * 4 * 0x1p-23 // 4 * FLT_EPSILON
				valueMatch = absDiff <= ulpSlack
			}
		}
	case string:
		v, ok := actual.(string)
		typeMatch = ok
		valueMatch = ok && (u == v)
	case []interface{}:
		v, ok := actual.([]interface{})
		typeMatch = ok
		if ok {
			if len(u) == len(v) {
				for i, e := range u {
					if failure, expected, actual, err := compare(e, v[i], fmt.Sprintf("%s/%d", path, i)); failure != "" || err != nil {
						return failure, expected, actual, err
					}
				}
				valueMatch = true
			} else {
				return fmt.Sprintf("Unexpected number of elements at %s", color.CyanString(path)),
					color.CyanString(strconv.Itoa(len(u))),
					color.RedString(strconv.Itoa(len(v))),
					nil
			}
		}
	case map[string]interface{}:
		v, ok := actual.(map[string]interface{})
		typeMatch = ok
		if ok {
			for n, e := range u {
				childPath := fmt.Sprintf("%s/%s", path, strings.ReplaceAll(strings.ReplaceAll(n, "~", "~0"), "/", "~1"))
				f, ok := v[n]
				if !ok {
					return fmt.Sprintf("Missing expected field at %s", color.RedString(childPath)), "", "", nil
				}
				if failure, expected, actual, err := compare(e, f, childPath); failure != "" || err != nil {
					return failure, expected, actual, err
				}
			}
			valueMatch = true
		}
	default:
		return "", "", "", fmt.Errorf("unexpected JSON type for value '%v'", expected)
	}

	if !valueMatch {
		if path == "" {
			path = "root"
		}
		mismatched := "type"
		if typeMatch {
			mismatched = "value"
		}
		expectedJson, _ := json.Marshal(expected)
		actualJson, _ := json.Marshal(actual)
		return fmt.Sprintf("Unexpected %s at %s", mismatched, color.CyanString(path)),
			color.CyanString(string(expectedJson)),
			color.RedString(string(actualJson)),
			nil
	}
	return "", "", "", nil
}

func getParameters(parametersRaw []byte, testsPath string) (map[string]string, error) {
	if parametersRaw != nil {
		var parametersPath string
		if err := json.Unmarshal(parametersRaw, &parametersPath); err == nil {
			if err = validateRelativePath(parametersPath); err != nil {
				return nil, err
			}
			resolvedParametersPath := filepath.Join(testsPath, parametersPath)
			parametersRaw, err = os.ReadFile(resolvedParametersPath)
			if err != nil {
				return nil, fmt.Errorf("failed to read request parameters at %s: %w", resolvedParametersPath, err)
			}
		}
		var parameters map[string]string
		if err := json.Unmarshal(parametersRaw, &parameters); err != nil {
			return nil, fmt.Errorf("request parameters must be JSON with only string values: %w", err)
		}
		return parameters, nil
	}
	return make(map[string]string), nil
}

func getBody(bodyRaw []byte, testsPath string) ([]byte, error) {
	var bodyPath string
	if err := json.Unmarshal(bodyRaw, &bodyPath); err == nil {
		if err = validateRelativePath(bodyPath); err != nil {
			return nil, err
		}
		resolvedBodyPath := filepath.Join(testsPath, bodyPath)
		bodyRaw, err = os.ReadFile(resolvedBodyPath)
		if err != nil {
			return nil, fmt.Errorf("failed to read body file at %s: %w", resolvedBodyPath, err)
		}
	}
	return bodyRaw, nil
}

func validateRelativePath(relPath string) error {
	if filepath.IsAbs(relPath) {
		return fmt.Errorf("path must be relative, but was '%s'", relPath)
	}
	cleanPath := filepath.Clean(relPath)
	if strings.HasPrefix(cleanPath, "../../../") {
		return fmt.Errorf("path may not point outside src/test/application, but '%s' does", relPath)
	}
	return nil
}

type test struct {
	Name     string   `json:"name"`
	Defaults defaults `json:"defaults"`
	Steps    []step   `json:"steps"`
}

type defaults struct {
	Cluster       string          `json:"cluster"`
	ParametersRaw json.RawMessage `json:"parameters"`
}

type step struct {
	Name     string   `json:"name"`
	Request  request  `json:"request"`
	Response response `json:"response"`
}

type request struct {
	Cluster       string            `json:"cluster"`
	Method        string            `json:"method"`
	URI           string            `json:"uri"`
	Headers       map[string]string `json:"headers"`
	ParametersRaw json.RawMessage   `json:"parameters"`
	BodyRaw       json.RawMessage   `json:"body"`
}

type response struct {
	Code    int             `json:"code"`
	BodyRaw json.RawMessage `json:"body"`
}

type testContext struct {
	cli        *CLI
	lazyTarget vespa.Target
	testsPath  string
	dryRun     bool
	authMethod string // "mtls" or "token"
	// Cache of services by their cluster name
	clusters map[string]*vespa.Service
}

func (t *testContext) target() (vespa.Target, error) {
	if t.lazyTarget == nil {
		target, err := t.cli.target(targetOptions{})
		if err != nil {
			return nil, err
		}
		t.lazyTarget = target
	}
	return t.lazyTarget, nil
}

type seenClusters map[string]bool

func (s seenClusters) warmup(step step, defaultCluster string, defaultParameters map[string]string, context testContext, waiter *Waiter) {
	// Determine which cluster to use
	cluster := step.Request.Cluster
	if cluster == "" {
		cluster = defaultCluster
	}

	// Skip if already warmed up
	if s[cluster] {
		context.cli.printDebug("warmup: cluster '", cluster, "' already warmed up, skipping")
		return
	}

	// Skip in dry-run mode
	if context.dryRun {
		return
	}

	// Check if this is an external endpoint (only if URI is explicitly set)
	if step.Request.URI != "" {
		requestUrl, err := url.ParseRequestURI(step.Request.URI)
		if err != nil {
			context.cli.printInfo("warmup: failed to parse URI ", step.Request.URI, ": ", err)
			return
		}
		if requestUrl.IsAbs() {
			context.cli.printDebug("warmup: skipping external endpoint: ", step.Request.URI)
			return
		}
	}

	// Skip for production tests
	if filepath.Base(context.testsPath) == "production-test" {
		context.cli.printDebug("warmup: skipping production test")
		return
	}

	// Skip if no waiter available
	if waiter == nil {
		context.cli.printInfo("warmup: no waiter available, skipping")
		return
	}

	// Get target
	target, err := context.target()
	if err != nil {
		context.cli.printInfo("warmup: failed to get target for cluster ", cluster, ": ", err)
		return
	}

	// Discover and cache the service if not already cached
	service, ok := context.clusters[cluster]
	if !ok {
		context.cli.printDebug("warmup: discovering service for cluster ", cluster)
		service, err = waiter.ServiceWithAuthMethod(target, cluster, context.authMethod)
		if err != nil {
			context.cli.printInfo("warmup: failed to discover service for cluster ", cluster, ": ", err)
			return
		}
		if context.authMethod == "token" {
			service.TLSOptions.CertificateFile = ""
			service.TLSOptions.PrivateKeyFile = ""
		}
		context.clusters[cluster] = service
	}

	// Make a simple GET / request to warm up the cluster
	fullURL := joinURL(service.BaseURL, "/")
	warmupUrl, err := url.ParseRequestURI(fullURL)
	if err != nil {
		context.cli.printInfo("warmup: failed to parse warmup URL ", fullURL, ": ", err)
		return
	}

	header := http.Header{}
	header.Set("Content-Type", "application/json")
	if context.authMethod == "token" {
		if err := context.cli.addBearerToken(&header); err != nil {
			context.cli.printInfo("warmup: failed to add bearer token: ", err)
			return
		}
	}

	context.cli.printDebug("warmup: sending GET ", warmupUrl.String(), " for cluster ", cluster)

	// Execute the warmup request with retries
	maxRetries := 10
	var lastErr error
	for attempt := 1; attempt <= maxRetries; attempt++ {
		request := &http.Request{
			URL:    warmupUrl,
			Method: "GET",
			Header: header,
			Body:   nil,
		}

		_, err = service.Do(request, 60*time.Second)

		if err == nil {
			// Success - mark cluster as seen
			context.cli.printDebug("warmup: successfully warmed up cluster ", cluster)
			s[cluster] = true
			return
		}

		lastErr = err
		context.cli.printDebug("warmup: attempt ", attempt, " failed for cluster ", cluster, ": ", err)
		if attempt < maxRetries {
			// Linear backoff: 1s, 2s, 3s, ..., 10s
			backoff := time.Duration(attempt) * time.Second
			context.cli.printDebug("warmup: retrying in", backoff)
			time.Sleep(backoff)
		}
	}

	// All retries failed
	context.cli.printInfo("warmup: failed to warm up cluster ", cluster, " after ", maxRetries, " attempts: ", lastErr)
}
