// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa test command
// Author: jonmv

package cmd

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
	"io"
	"io/ioutil"
	"math"
	"net/http"
	"net/url"
	"os"
	"path"
	"strings"
	"time"
)

func init() {
	rootCmd.AddCommand(testCmd)
}

var testCmd = &cobra.Command{
	Use:   "test [tests directory or test file]",
	Short: "Run a test suite, or a single test",
	Long: `Run a test suite, or a single test

Runs all JSON test files in the specified directory, or the single JSON
test file specified.

If no directory or file is specified, the working directory is used instead.`,
	Example: `$ vespa test src/test/application/tests/system-test
$ vespa test src/test/application/tests/system-test/feed-and-query.json`,
	Args:              cobra.MaximumNArgs(1),
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		target := getTarget()
		testPath := "."
		if len(args) > 0 {
			testPath = args[0]
		}
		if count, failed := runTests(testPath, target); len(failed) != 0 {
			_, _ = fmt.Fprintf(stdout, "\nFailed %d of %d tests:\n", len(failed), count)
			for _, test := range failed {
				_, _ = fmt.Fprintln(stdout, test)
			}
			exitFunc(3)
		} else if count == 0 {
			_, _ = fmt.Fprintf(stdout, "Failed to find any tests at '%v'\n", testPath)
			exitFunc(3)
		} else {
			_, _ = fmt.Fprintf(stdout, "%d tests completed successfully\n", count)
		}
	},
}

func runTests(rootPath string, target vespa.Target) (int, []string) {
	count := 0
	failed := make([]string, 0)
	if stat, err := os.Stat(rootPath); err != nil {
		fatalErr(err, "Failed reading specified test path")
	} else if stat.IsDir() {
		tests, err := os.ReadDir(rootPath)
		if err != nil {
			fatalErr(err, "Failed reading specified test directory")
		}
		for _, test := range tests {
			if !test.IsDir() && strings.HasSuffix(test.Name(), ".json") {
				testPath := path.Join(rootPath, test.Name())
				failure := runTest(testPath, target)
				if failure != "" {
					failed = append(failed, failure)
				}
				count++
			}
		}
	} else if strings.HasSuffix(stat.Name(), ".json") {
		failure := runTest(rootPath, target)
		if failure != "" {
			failed = append(failed, failure)
		}
		count++
	}
	return count, failed
}

// Runs the test at the given path, and returns the specified test name if the test fails
func runTest(testPath string, target vespa.Target) string {
	var test test
	bytes := readFile(testPath)
	if err := json.Unmarshal(bytes, &test); err != nil {
		fatalErr(err, fmt.Sprintf("Failed to parse test file '%s", testPath))
	}

	testName := test.Name
	if test.Name == "" {
		testName = testPath
	}
	_, _ = fmt.Fprintf(stdout, "Running %s: ", testName)

	defaultParameters, err := getParameters(test.Defaults.ParametersRaw, path.Dir(testPath))
	if err != nil {
		fatalErr(err, fmt.Sprintf("Invalid default parameters for '%s'", testName))
	}

	for i, assertion := range test.Assertions {
		assertionName := assertion.Name
		if assertionName == "" {
			assertionName = fmt.Sprintf("assertion %d", i)
		}
		failure, err := verify(assertion, path.Dir(testPath), test.Defaults.Cluster, defaultParameters, target)
		if err != nil {
			fatalErr(err, fmt.Sprintf("\nError verifying %s", assertionName))
		}
		if failure != "" {
			_, _ = fmt.Fprintf(stdout, "\nFailed verifying %s: \n%s\n", assertionName, failure)
			return fmt.Sprintf("%v: %v", testName, assertionName)
		}
		fmt.Print(".")
	}
	fmt.Println(" OK!")
	return ""
}

// Asserts specified response is obtained for request, or returns a failure message, or an error if this fails
func verify(assertion assertion, testsPath string, defaultCluster string, defaultParameters map[string]string, target vespa.Target) (string, error) {
	requestBody, err := getBody(assertion.Request.BodyRaw, testsPath)
	if err != nil {
		return "", err
	}

	parameters, err := getParameters(assertion.Request.ParametersRaw, testsPath)
	if err != nil {
		return "", err
	}
	for name, value := range defaultParameters {
		if _, present := parameters[name]; !present {
			parameters[name] = value
		}
	}

	cluster := assertion.Request.Cluster
	if cluster == "" {
		cluster = defaultCluster
	}

	service, err := target.Service("query", 0, 0, cluster)
	if err != nil {
		return "", err
	}

	method := assertion.Request.Method
	if method == "" {
		method = "GET"
	}

	pathAndQuery := assertion.Request.URI
	if pathAndQuery == "" {
		pathAndQuery = "/search/"
	}
	requestUrl, err := url.ParseRequestURI(service.BaseURL + pathAndQuery)
	if err != nil {
		return "", err
	}
	query := requestUrl.Query()
	for name, value := range parameters {
		query.Add(name, value)
	}
	requestUrl.RawQuery = query.Encode()

	header := http.Header{}
	header.Add("Content-Type", "application/json")

	request := &http.Request{
		URL:    requestUrl,
		Method: method,
		Header: header,
		Body:   ioutil.NopCloser(bytes.NewReader(requestBody)),
	}
	defer request.Body.Close()

	response, err := service.Do(request, 600*time.Second) // Vespa should provide a response within the given request timeout
	if err != nil {
		return "", err
	}
	defer response.Body.Close()

	statusCode := assertion.Response.Code
	if statusCode == 0 {
		statusCode = 200
	}
	if statusCode != response.StatusCode {
		return fmt.Sprintf("Expected status code %d, but got %d, and body:\n%s", statusCode, response.StatusCode, util.ReaderToJSON(response.Body)), nil
	}

	responseBodySpecBytes, err := getBody(assertion.Response.BodyRaw, testsPath)
	if err != nil {
		return "", err
	}
	if responseBodySpecBytes == nil {
		return "", nil
	}
	var responseBodySpec interface{}
	err = json.Unmarshal(responseBodySpecBytes, &responseBodySpec)
	if err != nil {
		return "", err
	}

	responseBodyBytes, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return "", err
	}
	var responseBody interface{}
	err = json.Unmarshal(responseBodyBytes, &responseBody)
	if err != nil {
		return "", err
	}

	return compare(responseBodySpec, responseBody, "")
}

func compare(expected interface{}, actual interface{}, path string) (string, error) {
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
		if ok && len(u) == len(v) {
			for i, e := range u {
				result, err := compare(e, v[i], fmt.Sprintf("%s[%d]", path, i))
				if result != "" || err != nil {
					return result, err
				}
			}
			valueMatch = true
		}
	case map[string]interface{}:
		v, ok := actual.(map[string]interface{})
		typeMatch = ok
		if ok {
			for n, e := range u {
				result, err := compare(e, v[n], fmt.Sprintf("%s['%s']", path, n))
				if result != "" || err != nil {
					return result, err
				}
			}
			valueMatch = true
		}
	default:
		return "", fmt.Errorf("unexpected expected JSON type for value '%v'", expected)
	}

	if !(typeMatch && valueMatch) {
		if path == "" {
			path = "root"
		}
		expectedJson, _ := json.MarshalIndent(expected, "", "  ")
		actualJson, _ := json.MarshalIndent(actual, "", "  ")
		mismatched := "Type"
		if typeMatch {
			mismatched = "Value"
		}
		return fmt.Sprintf("%s mismatch at %s: <<< expected === actual >>>\n<<<\n%s\n===\n%s\n>>>\n", mismatched, path, expectedJson, actualJson), nil
	}
	return "", nil
}

func getParameters(parametersRaw []byte, testsPath string) (map[string]string, error) {
	if parametersRaw != nil {
		var parametersPath string
		if err := json.Unmarshal(parametersRaw, &parametersPath); err == nil {
			parametersRaw = readFile(path.Join(testsPath, parametersPath))
		}
		var parameters map[string]string
		if err := json.Unmarshal(parametersRaw, &parameters); err != nil {
			return nil, fmt.Errorf("request parameters must be JSON with only string values: %v", err)
		}
		return parameters, nil
	}
	return make(map[string]string), nil
}

func getBody(bodyRaw []byte, testsPath string) ([]byte, error) {
	var bodyPath string
	if err := json.Unmarshal(bodyRaw, &bodyPath); err == nil {
		return readFile(path.Join(testsPath, bodyPath)), nil
	}
	return bodyRaw, nil
}

func readFile(filePath string) []byte {
	file, err := os.Open(filePath)
	if err != nil {
		fatalErr(err, fmt.Sprintf("Failed to open file at '%v'", filePath))
	}
	fileBytes, err := io.ReadAll(file)
	if err != nil {
		fatalErr(err, fmt.Sprintf("Failed to read file at '%v'", filePath))
	}
	err = file.Close()
	if err != nil {
		fatalErr(err, fmt.Sprintf("Failed to closse file at '%v'", filePath))
	}
	return fileBytes
}

type test struct {
	Name       string      `json:"name"`
	Defaults   defaults    `json:"defaults"`
	Assertions []assertion `json:"assertions"`
}

type defaults struct {
	Cluster       string          `json:"cluster"`
	ParametersRaw json.RawMessage `json:"parameters"`
}

type assertion struct {
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
