// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A helper for testing commands
// Author: bratseth

package cmd

import (
	"bytes"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strconv"
	"testing"
	"time"

	"github.com/logrusorgru/aurora"
	"github.com/spf13/viper"
	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/util"
)

func executeCommand(t *testing.T, client *mockHttpClient, args []string, moreArgs []string) string {
	if client != nil {
		util.ActiveHttpClient = client
	}

	// Never print colors in tests
	color = aurora.NewAurora(false)

	// Use a separate config dir for each test
	os.Setenv("VESPA_CLI_HOME", t.TempDir())
	if len(args) > 0 && args[0] != "config" {
		viper.Reset() // Reset config unless we're testing the config sub-command
	}

	// Reset to default target - persistent flags in Cobra persists over tests
	log.SetOutput(bytes.NewBufferString(""))
	rootCmd.SetArgs([]string{"status", "-t", "local"})
	rootCmd.Execute()

	// Capture stdout and execute command
	b := bytes.NewBufferString("")
	log.SetOutput(b)
	rootCmd.SetArgs(append(args, moreArgs...))
	rootCmd.Execute()
	out, err := ioutil.ReadAll(b)
	assert.Empty(t, err, "No error")
	return string(out)
}

type mockHttpClient struct {
	// The HTTP status code that will be returned from the next invocation. Default: 200
	nextStatus int

	// The response body code that will be returned from the next invocation. Default: ""
	nextBody string

	// A recording of the last HTTP request made through this
	lastRequest *http.Request
}

func (c *mockHttpClient) Do(request *http.Request, timeout time.Duration) (response *http.Response, error error) {
	if c.nextStatus == 0 {
		c.nextStatus = 200
	}
	c.lastRequest = request
	return &http.Response{
			Status:     "Status " + strconv.Itoa(c.nextStatus),
			StatusCode: c.nextStatus,
			Body:       ioutil.NopCloser(bytes.NewBufferString(c.nextBody)),
			Header:     make(http.Header),
		},
		nil
}
