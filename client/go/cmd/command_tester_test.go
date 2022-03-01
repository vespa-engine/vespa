// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// A helper for testing commands
// Author: bratseth

package cmd

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"testing"

	"github.com/spf13/pflag"
	"github.com/spf13/viper"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/mock"
	"github.com/vespa-engine/vespa/client/go/util"
)

type command struct {
	homeDir         string
	cacheDir        string
	stdin           io.ReadWriter
	args            []string
	moreArgs        []string
	env             map[string]string
	failTestOnError bool
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

func setEnv(env map[string]string) map[string]string {
	originalEnv := map[string]string{}
	for k, v := range env {
		value, ok := os.LookupEnv(k)
		if ok {
			originalEnv[k] = value
		}
		os.Setenv(k, v)
	}
	return originalEnv
}

func resetEnv(env map[string]string, original map[string]string) {
	for k := range env {
		if v, ok := original[k]; ok {
			os.Setenv(k, v)
		} else {
			os.Unsetenv(k)
		}
	}
}

func execute(cmd command, t *testing.T, client *mock.HTTPClient) (string, string) {
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

	env := map[string]string{}
	for k, v := range cmd.env {
		env[k] = v
	}
	env["VESPA_CLI_HOME"] = cmd.homeDir
	env["VESPA_CLI_CACHE_DIR"] = cmd.cacheDir
	originalEnv := setEnv(env)
	defer resetEnv(env, originalEnv)

	// Reset viper at end of test to ensure vespa config set does not leak between tests
	t.Cleanup(viper.Reset)

	// Reset flags to their default value - persistent flags in Cobra persists over tests
	// TODO: Due to the bad design of viper, the only proper fix is to get rid of global state by moving each command to
	// their own sub-package
	rootCmd.Flags().VisitAll(resetFlag)
	queryCmd.Flags().VisitAll(resetFlag)
	documentCmd.Flags().VisitAll(resetFlag)
	logCmd.Flags().VisitAll(resetFlag)
	certCmd.Flags().VisitAll(resetFlag)
	certAddCmd.Flags().VisitAll(resetFlag)

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
	err := Execute()
	if cmd.failTestOnError {
		require.Nil(t, err)
	}
	return capturedOut.String(), capturedErr.String()
}

func executeCommand(t *testing.T, client *mock.HTTPClient, args []string, moreArgs []string) string {
	out, _ := execute(command{args: args, moreArgs: moreArgs}, t, client)
	return out
}
