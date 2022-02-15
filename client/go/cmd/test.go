// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa test command
// Author: jonmv

package cmd

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"math"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func init() {
	rootCmd.AddCommand(testCmd)
	testCmd.PersistentFlags().StringVarP(&zoneArg, zoneFlag, "z", "dev.aws-us-east-1c", "The zone to use for deployment")
}

var testCmd = &cobra.Command{
	Use:   "test <tests directory or test file>",
	Short: "Run a test suite, or a single test",
	Long: `Run a test suite, or a single test

Runs all JSON test files in the specified directory, or the single JSON test file specified.

See https://cloud.vespa.ai/en/reference/testing.html for details.`,
	Example: `$ vespa test src/test/application/tests/system-test
$ vespa test src/test/application/tests/system-test/feed-and-query.json`,
	Args:              cobra.ExactArgs(1),
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		count, failed, err := runTests(args[0], false)
		if err != nil {
			return err
		}
		if len(failed) != 0 {
			plural := "s"
			if count == 1 {
				plural = ""
			}
			fmt.Fprintf(stdout, "\n%s %d of %d test%s failed:\n", color.Red("Failure:"), len(failed), count, plural)
			for _, test := range failed {
				fmt.Fprintln(stdout, test)
			}
			return ErrCLI{Status: 3, error: fmt.Errorf("tests failed"), quiet: true}
		} else {
			plural := "s"
			if count == 1 {
				plural = ""
			}
			fmt.Fprintf(stdout, "\n%s %d test%s OK\n", color.Green("Success:"), count, plural)
			return nil
		}
	},
}

func runTests(rootPath string, dryRun bool) (int, []string, error) {
	count := 0
	failed := make([]string, 0)
	if stat, err := os.Stat(rootPath); err != nil {
		return 0, nil, errHint(err, "See https://cloud.vespa.ai/en/reference/testing")
	} else if stat.IsDir() {
		tests, err := ioutil.ReadDir(rootPath) // TODO: Use os.ReadDir when >= 1.16 is required.
		if err != nil {
			return 0, nil, errHint(err, "See https://cloud.vespa.ai/en/reference/testing")
		}
		context := testContext{testsPath: rootPath, dryRun: dryRun}
		previousFailed := false
		for _, test := range tests {
			if !test.IsDir() && filepath.Ext(test.Name()) == ".json" {
				testPath := filepath.Join(rootPath, test.Name())
				if previousFailed {
					fmt.Fprintln(stdout, "")
					previousFailed = false
				}
				failure, err := runTest(testPath, context)
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
		failure, err := runTest(rootPath, testContext{testsPath: filepath.Dir(rootPath), dryRun: dryRun})
		if err != nil {
			return 0, nil, err
		}
		if failure != "" {
			failed = append(failed, failure)
		}
		count++
	}
	if count == 0 {
		return 0, nil, errHint(fmt.Errorf("failed to find any tests at %s", rootPath), "See https://cloud.vespa.ai/en/reference/testing")
	}
	return count, failed, nil
}

// Runs the test at the given path, and returns the specified test name if the test fails
func runTest(testPath string, context testContext) (string, error) {
	var test test
	testBytes, err := ioutil.ReadFile(testPath)
	if err != nil {
		return "", errHint(err, "See https://cloud.vespa.ai/en/reference/testing")
	}
	if err = json.Unmarshal(testBytes, &test); err != nil {
		return "", errHint(fmt.Errorf("failed parsing test at %s: %w", testPath, err), "See https://cloud.vespa.ai/en/reference/testing")
	}

	testName := test.Name
	if test.Name == "" {
		testName = filepath.Base(testPath)
	}
	if !context.dryRun {
		fmt.Fprintf(stdout, "%s:", testName)
	}

	defaultParameters, err := getParameters(test.Defaults.ParametersRaw, filepath.Dir(testPath))
	if err != nil {
		fmt.Fprintln(stderr)
		return "", errHint(fmt.Errorf("invalid default parameters for %s: %w", testName, err), "See https://cloud.vespa.ai/en/reference/testing")
	}

	if len(test.Steps) == 0 {
		fmt.Fprintln(stderr)
		return "", errHint(fmt.Errorf("a test must have at least one step, but none were found in %s", testPath), "See https://cloud.vespa.ai/en/reference/testing")
	}
	for i, step := range test.Steps {
		stepName := fmt.Sprintf("Step %d", i+1)
		if step.Name != "" {
			stepName += ": " + step.Name
		}
		failure, longFailure, err := verify(step, test.Defaults.Cluster, defaultParameters, context)
		if err != nil {
			fmt.Fprintln(stderr)
			return "", errHint(fmt.Errorf("error in %s: %w", stepName, err), "See https://cloud.vespa.ai/en/reference/testing")
		}
		if !context.dryRun {
			if failure != "" {
				fmt.Fprintf(stdout, " %s\n%s:\n%s\n", color.Red("failed"), stepName, longFailure)
				return fmt.Sprintf("%s: %s: %s", testName, stepName, failure), nil
			}
			if i == 0 {
				fmt.Fprintf(stdout, " ")
			}
			fmt.Fprint(stdout, ".")
		}
	}
	if !context.dryRun {
		fmt.Fprintln(stdout, color.Green(" OK"))
	}
	return "", nil
}

// Asserts specified response is obtained for request, or returns a failure message, or an error if this fails
func verify(step step, defaultCluster string, defaultParameters map[string]string, context testContext) (string, string, error) {
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
	if !externalEndpoint && !context.dryRun {
		target, err := context.target()
		if err != nil {
			return "", "", err
		}
		service, err = target.Service("query", 0, 0, cluster)
		if err != nil {
			return "", "", err
		}
		requestUrl, err = url.ParseRequestURI(service.BaseURL + requestUri)
		if err != nil {
			return "", "", err
		}
	}
	query := requestUrl.Query()
	for name, value := range parameters {
		query.Add(name, value)
	}
	requestUrl.RawQuery = query.Encode()

	header := http.Header{}
	header.Add("Content-Type", "application/json") // TODO: Not guaranteed to be true ...

	request := &http.Request{
		URL:    requestUrl,
		Method: method,
		Header: header,
		Body:   ioutil.NopCloser(bytes.NewReader(requestBody)),
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
		util.ActiveHttpClient.UseCertificate([]tls.Certificate{})
		response, err = util.ActiveHttpClient.Do(request, 60*time.Second)
	} else {
		response, err = service.Do(request, 600*time.Second) // Vespa should provide a response within the given request timeout
	}
	if err != nil {
		return "", "", err
	}
	defer response.Body.Close()

	if statusCode != response.StatusCode {
		return fmt.Sprintf("Unexpected status code: %d", color.Red(response.StatusCode)),
			fmt.Sprintf("Unexpected status code\nExpected: %d\nActual:   %d\nRequested: %s at %s\nResponse:\n%s",
				color.Cyan(statusCode),
				color.Red(response.StatusCode),
				color.Cyan(method),
				color.Cyan(requestUrl),
				util.ReaderToJSON(response.Body)), nil
	}

	if responseBodySpec == nil {
		return "", "", nil
	}

	responseBodyBytes, err := ioutil.ReadAll(response.Body)
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
		longFailure += fmt.Sprintf("\nRequested: %s at %s\nResponse:\n%s", color.Cyan(method), color.Cyan(requestUrl), string(responsePretty))
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
		valueMatch = ok && math.Abs(u-v) < 1e-9
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
				return fmt.Sprintf("Unexpected number of elements at %s", color.Cyan(path)),
					fmt.Sprintf("%d", color.Cyan(len(u))),
					fmt.Sprintf("%d", color.Red(len(v))),
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
					return fmt.Sprintf("Missing expected field at %s", color.Red(childPath)), "", "", nil
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
		return fmt.Sprintf("Unexpected %s at %s", mismatched, color.Cyan(path)),
			fmt.Sprintf("%s", color.Cyan(expectedJson)),
			fmt.Sprintf("%s", color.Red(actualJson)),
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
			parametersRaw, err = ioutil.ReadFile(resolvedParametersPath)
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
		bodyRaw, err = ioutil.ReadFile(resolvedBodyPath)
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
	Cluster       string          `json:"cluster"`
	Method        string          `json:"method"`
	URI           string          `json:"uri"`
	ParametersRaw json.RawMessage `json:"parameters"`
	BodyRaw       json.RawMessage `json:"body"`
}

type response struct {
	Code    int             `json:"code"`
	BodyRaw json.RawMessage `json:"body"`
}

type testContext struct {
	lazyTarget vespa.Target
	testsPath  string
	dryRun     bool
}

func (t *testContext) target() (vespa.Target, error) {
	if t.lazyTarget == nil {
		target, err := getTarget()
		if err != nil {
			return nil, err
		}
		t.lazyTarget = target
	}
	return t.lazyTarget, nil
}
