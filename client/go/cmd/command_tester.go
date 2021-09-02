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
	"strconv"
	"testing"
	"time"

	"github.com/logrusorgru/aurora"
	"github.com/spf13/pflag"
	"github.com/spf13/viper"
	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/util"
)

type command struct {
	configDir string
	args      []string
	moreArgs  []string
}

func execute(cmd command, t *testing.T, client *mockHttpClient) string {
	if client != nil {
		util.ActiveHttpClient = client
	}

	// Never print colors in tests
	color = aurora.NewAurora(false)

	// Set config dir. Use a separate one per test if none is specified
	if cmd.configDir == "" {
		cmd.configDir = t.TempDir()
		viper.Reset()
	}
	os.Setenv("VESPA_CLI_HOME", cmd.configDir)

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
	// The HTTP status code that will be returned from the next invocation. Default: 200
	nextStatus int

	// The response body code that will be returned from the next invocation. Default: ""
	nextBody string

	// A recording of the last HTTP request made through this
	lastRequest *http.Request

	// All requests made through this
	requests []*http.Request
}

func (c *mockHttpClient) Do(request *http.Request, timeout time.Duration) (response *http.Response, error error) {
	if c.nextStatus == 0 {
		c.nextStatus = 200
	}
	c.lastRequest = request
	c.requests = append(c.requests, request)
	return &http.Response{
			Status:     "Status " + strconv.Itoa(c.nextStatus),
			StatusCode: c.nextStatus,
			Body:       ioutil.NopCloser(bytes.NewBufferString(c.nextBody)),
			Header:     make(http.Header),
		},
		nil
}

func (c *mockHttpClient) UseCertificate(certificate tls.Certificate) {}
