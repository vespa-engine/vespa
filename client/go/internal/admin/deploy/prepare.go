// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"fmt"
	"os"
	"strconv"
)

// main entry point for vespa-deploy prepare

func looksLikeNumber(s string) bool {
	var i, j int
	n, err := fmt.Sscanf(s+" 123", "%d %d", &i, &j)
	return n == 2 && err == nil
}

func RunPrepare(opts *Options, args []string) (err error) {
	var response string
	if len(args) == 0 {
		// prepare last upload
		sessId := getSessionIdFromFile(opts.Tenant)
		response, err = doPrepare(opts, sessId)
	} else if isFileOrDir(args[0]) {
		err := RunUpload(opts, args)
		if err != nil {
			return err
		}
		return RunPrepare(opts, []string{})
	} else if looksLikeNumber(args[0]) {
		response, err = doPrepare(opts, args[0])
	} else {
		err = fmt.Errorf("Command failed. No directory or zip file found: '%s'", args[0])
	}
	if err != nil {
		return err
	}
	var result PrepareResult
	code, err := decodeResponse(response, &result)
	if err != nil {
		return err
	}
	for _, entry := range result.Log {
		fmt.Println(entry.Level+":", entry.Message)
	}
	if code != 200 {
		return fmt.Errorf("Request failed. HTTP status code: %d\n%s", code, result.Message)
	}
	fmt.Println(result.Message)
	return err
}

func isFileOrDir(name string) bool {
	f, err := os.Open(name)
	if err != nil {
		return false
	}
	st, err := f.Stat()
	if err != nil {
		return false
	}
	return st.Mode().IsRegular() || st.Mode().IsDir()
}

func doPrepare(opts *Options, sessionId string) (output string, err error) {
	src := makeConfigsourceUrl(opts)
	url := src + pathPrefix(opts) + "/" + sessionId + "/prepared"
	url = addUrlPropertyFromFlag(url, opts.Force, "ignoreValidationErrors")
	url = addUrlPropertyFromFlag(url, opts.DryRun, "dryRun")
	url = addUrlPropertyFromFlag(url, opts.Verbose, "verbose")
	url = addUrlPropertyFromFlag(url, opts.Hosted, "hostedVespa")
	url = addUrlPropertyFromOption(url, opts.Application, "applicationName")
	url = addUrlPropertyFromOption(url, opts.Instance, "instance")
	url = addUrlPropertyFromOption(url, strconv.Itoa(opts.Timeout), "timeout")
	url = addUrlPropertyFromOption(url, opts.Rotations, "rotations")
	url = addUrlPropertyFromOption(url, opts.VespaVersion, "vespaVersion")
	fmt.Printf("Preparing session %s using %s\n", sessionId, urlWithoutQuery(url))
	output, err = curlPutNothing(url)
	return
}
