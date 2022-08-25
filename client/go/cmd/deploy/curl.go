// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"

	"github.com/vespa-engine/vespa/client/go/vespa"
)

func curlPut(url string, cfgSrc string) (string, error) {
	args := append(curlPutArgs(), url)
	return runCurl(args, new(strings.Reader), cfgSrc)
}

func curlPost(url string, input io.Reader, cfgSrc string) (string, error) {
	args := append(curlPostArgs(), url)
	return runCurl(args, input, cfgSrc)
}

func curlPostZip(url string, input io.Reader, cfgSrc string) (string, error) {
	args := append(curlPostZipArgs(), url)
	return runCurl(args, input, cfgSrc)
}

func curlGet(url string, output io.Writer) error {
	args := append(curlGetArgs(), url)
	cmd := exec.Command(curlWrapper(), args...)
	cmd.Stdout = output
	cmd.Stderr = os.Stderr
	// fmt.Printf("running command: %v\n", cmd)
	err := cmd.Run()
	return err
}

func urlWithoutQuery(url string) string {
	parts := strings.Split(url, "?")
	return parts[0]
}

func getOutputFromCmd(program string, args ...string) (string, error) {
	cmd := exec.Command(program, args...)
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = os.Stderr
	err := cmd.Run()
	return out.String(), err
}

func runCurl(args []string, input io.Reader, cfgSrc string) (string, error) {
	cmd := exec.Command(curlWrapper(), args...)
	cmd.Stdin = input
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = os.Stderr
	// fmt.Printf("running command: %v\n", cmd)
	err := cmd.Run()
	// fmt.Printf("output: %s\n", out.String())
	if err != nil {
		if cmd.ProcessState.ExitCode() == 7 {
			return "", fmt.Errorf("HTTP request failed. Could not connect to %s", cfgSrc)
		}
		return "", fmt.Errorf("HTTP request failed with curl %s", err.Error())
	}
	return out.String(), err
}

func curlWrapper() string {
	return vespa.FindHome() + "/libexec/vespa/vespa-curl-wrapper"
}

func commonCurlArgs() []string {
	return []string{
		"-A", "vespa-deploy",
		"--silent",
		"--show-error",
		"--connect-timeout", "30",
		"--max-time", "1200",
	}
}

func curlPutArgs() []string {
	return append(commonCurlArgs(),
		"--write-out", "%{http_code}",
		"--request", "PUT")
}

func curlGetArgs() []string {
	return append(commonCurlArgs(),
		"--request", "GET")
}

func curlPostArgs() []string {
	return append(commonCurlArgs(),
		"--write-out", "%{http_code}",
		"--request", "POST",
		"--header", "Content-Type: application/x-gzip",
		"--data-binary", "@-")
}

func curlPostZipArgs() []string {
	return append(commonCurlArgs(),
		"--write-out", "%{http_code}",
		"--request", "POST",
		"--header", "Content-Type: application/zip",
		"--data-binary", "@-")
}
