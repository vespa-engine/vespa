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

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/curl"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func curlPutNothing(url string) (string, error) {
	cmd := newCurlCommand(url, curlPutArgs())
	cmd.Method = "PUT"
	var out bytes.Buffer
	err := runCurl(cmd, &out)
	return out.String(), err
}

func curlPost(url string, input io.Reader) (string, error) {
	cmd := newCurlCommand(url, curlPostArgs())
	cmd.Method = "POST"
	cmd.Header("Content-Type", "application/x-gzip")
	cmd.WithBodyInput(input)
	var out bytes.Buffer
	err := runCurl(cmd, &out)
	return out.String(), err
}

func curlPostZip(url string, input io.Reader) (string, error) {
	cmd := newCurlCommand(url, curlPostArgs())
	cmd.Method = "POST"
	cmd.Header("Content-Type", "application/zip")
	cmd.WithBodyInput(input)
	var out bytes.Buffer
	err := runCurl(cmd, &out)
	return out.String(), err
}

func curlGet(url string, output io.Writer) error {
	cmd := newCurlCommand(url, commonCurlArgs())
	err := runCurl(cmd, output)
	return err
}

func urlWithoutQuery(url string) string {
	parts := strings.Split(url, "?")
	return parts[0]
}

func newCurlCommand(url string, args []string) *curl.Command {
	tls, err := vespa.LoadTlsConfig()
	if err != nil {
		util.JustExitWith(err)
	}
	if tls != nil && strings.HasPrefix(url, "http:") {
		url = "https:" + url[5:]
	}
	cmd, err := curl.RawArgs(url, args...)
	if err != nil {
		util.JustExitWith(err)
	}
	if tls != nil {
		if tls.DisableHostnameValidation {
			cmd, err = curl.RawArgs(url, append(args, "--insecure")...)
			if err != nil {
				util.JustExitWith(err)
			}
		}
		cmd.PrivateKey = tls.Files.PrivateKey
		cmd.Certificate = tls.Files.Certificates
		cmd.CaCertificate = tls.Files.CaCertificates
	}
	return cmd
}

func runCurl(cmd *curl.Command, stdout io.Writer) error {
	trace.Trace("running curl:", cmd.String())
	err := cmd.Run(stdout, os.Stderr)
	if err != nil {
		if ee, ok := err.(*exec.ExitError); ok {
			if ee.ProcessState.ExitCode() == 7 {
				return fmt.Errorf("HTTP request failed. Could not connect to %s", cmd.GetUrlPrefix())
			}
		}
		return fmt.Errorf("HTTP request failed with curl %s", err.Error())
	}
	return err
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
		"--write-out", "\n%{http_code}")
}

func curlGetArgs() []string {
	return commonCurlArgs()
}

func curlPostArgs() []string {
	return append(commonCurlArgs(),
		"--write-out", "\n%{http_code}")
}
