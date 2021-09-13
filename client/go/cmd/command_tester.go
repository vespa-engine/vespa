// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A helper for testing commands
// Author: bratseth

package cmd

import (
	"bytes"
	"crypto/tls"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	"github.com/logrusorgru/aurora"
	"github.com/spf13/pflag"
	"github.com/spf13/viper"
	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/util"
)

type command struct {
	homeDir  string
	args     []string
	moreArgs []string
}

func execute(cmd command, t *testing.T, client *mockHttpClient) string {
	if client != nil {
		util.ActiveHttpClient = client
	}

	// Never print colors in tests
	color = aurora.NewAurora(false)

	// Set config dir. Use a separate one per test if none is specified
	if cmd.homeDir == "" {
		cmd.homeDir = t.TempDir()
		viper.Reset()
	}
	os.Setenv("VESPA_CLI_HOME", filepath.Join(cmd.homeDir, ".vespa"))

	// Reset flags to their default value - persistent flags in Cobra persists over tests
	rootCmd.Flags().VisitAll(func(f *pflag.Flag) {
		switch v := f.Value.(type) {
		case pflag.SliceValue:
			_ = v.Replace([]string{})
		default:
			switch v.Type() {
			case "bool", "string", "int":
				_ = v.Set(f.DefValue)
			}
		}
	})

	// Do not exit in tests
	exitFunc = func(code int) {}

	// Capture stdout and execute command
	var b bytes.Buffer
	log.SetOutput(&b)
	rootCmd.SetArgs(append(cmd.args, cmd.moreArgs...))
	rootCmd.Execute()
	out, err := ioutil.ReadAll(&b)
	assert.Nil(t, err, "No error")
	return string(out)
}

func executeCommand(t *testing.T, client *mockHttpClient, args []string, moreArgs []string) string {
	return execute(command{args: args, moreArgs: moreArgs}, t, client)
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

func convergeServices(client *mockHttpClient) { client.NextResponse(200, `{"converged":true}`) }
