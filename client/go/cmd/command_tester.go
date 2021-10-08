// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A helper for testing commands
// Author: bratseth

package cmd

import (
	"bytes"
	"crypto/tls"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	"github.com/spf13/pflag"
	"github.com/spf13/viper"
	"github.com/vespa-engine/vespa/client/go/util"
)

type command struct {
	homeDir  string
	cacheDir string
	stdin    io.ReadWriter
	args     []string
	moreArgs []string
}

func resetFlag(f *pflag.Flag) {
	switch v := f.Value.(type) {
	case pflag.SliceValue:
		_ = v.Replace([]string{})
	default:
		switch v.Type() {
		case "bool", "string", "int":
			_ = v.Set(f.DefValue)
		}
	}
}

func execute(cmd command, t *testing.T, client *mockHttpClient) (string, string) {
	if client != nil {
		util.ActiveHttpClient = client
	}

	// Set Vespa CLI directories. Use a separate one per test if none is specified
	if cmd.homeDir == "" {
		cmd.homeDir = filepath.Join(t.TempDir(), ".vespa")
		viper.Reset()
	}
	if cmd.cacheDir == "" {
		cmd.cacheDir = filepath.Join(t.TempDir(), ".cache", "vespa")
	}
	os.Setenv("VESPA_CLI_HOME", cmd.homeDir)
	os.Setenv("VESPA_CLI_CACHE_DIR", cmd.cacheDir)

	// Reset flags to their default value - persistent flags in Cobra persists over tests
	// TODO: Due to the bad design of viper, the only proper fix is to get rid of global state by moving each command to
	// their own sub-package
	rootCmd.Flags().VisitAll(resetFlag)
	documentCmd.Flags().VisitAll(resetFlag)

	// Do not exit in tests
	exitFunc = func(code int) {}

	// Capture stdout and execute command
	var capturedOut bytes.Buffer
	var capturedErr bytes.Buffer
	stdout = &capturedOut
	stderr = &capturedErr
	if cmd.stdin != nil {
		stdin = cmd.stdin
	} else {
		stdin = os.Stdin
	}

	// Execute command and return output
	rootCmd.SetArgs(append(cmd.args, cmd.moreArgs...))
	rootCmd.Execute()
	return capturedOut.String(), capturedErr.String()
}

func executeCommand(t *testing.T, client *mockHttpClient, args []string, moreArgs []string) string {
	out, _ := execute(command{args: args, moreArgs: moreArgs}, t, client)
	return out
}

type mockHttpClient struct {
	// The responses to return for future requests. Once a response is consumed, it's removed from this array
	nextResponses []mockResponse

	// A recording of the last HTTP request made through this
	lastRequest *http.Request

	// All requests made through this
	requests []*http.Request
}

type mockResponse struct {
	status int
	body   string
}

func (c *mockHttpClient) NextStatus(status int) { c.NextResponse(status, "") }

func (c *mockHttpClient) NextResponse(status int, body string) {
	c.nextResponses = append(c.nextResponses, mockResponse{status: status, body: body})
}

func (c *mockHttpClient) Do(request *http.Request, timeout time.Duration) (*http.Response, error) {
	response := mockResponse{status: 200}
	if len(c.nextResponses) > 0 {
		response = c.nextResponses[0]
		c.nextResponses = c.nextResponses[1:]
	}
	c.lastRequest = request
	c.requests = append(c.requests, request)
	return &http.Response{
			Status:     "Status " + strconv.Itoa(response.status),
			StatusCode: response.status,
			Body:       ioutil.NopCloser(bytes.NewBufferString(response.body)),
			Header:     make(http.Header),
		},
		nil
}

func (c *mockHttpClient) UseCertificate(certificate tls.Certificate) {}
