// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"fmt"
	"os"
	"os/exec"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
)

// main entry point for vespa-deploy upload

func RunUpload(opts *Options, args []string) error {
	output, err := doUpload(opts, args)
	if err != nil {
		return err
	}
	var result UploadResult
	code, err := decodeResponse(output, &result)
	if err != nil {
		return err
	}
	if code != 200 {
		return fmt.Errorf("Request failed. HTTP status code: %d\n%s", code, result.Message)
	}
	fmt.Println(result.Message)
	writeSessionIdToFile(opts.Tenant, result.SessionID)
	return nil
}

func doUpload(opts *Options, args []string) (result string, err error) {
	sources := makeConfigsourceUrls(opts)
	for idx, src := range sources {
		if idx > 0 {
			fmt.Println(err)
			fmt.Println("Retrying with another config server")
		}
		result, err = uploadToConfigSource(opts, src, args)
		if err == nil {
			writeConfigsourceUrlUsed(src)
			return
		}
	}
	return
}

func uploadToConfigSource(opts *Options, src string, args []string) (string, error) {
	if opts.From != "" {
		return uploadFrom(opts, src)
	}
	if len(args) == 0 {
		return uploadDirectory(opts, src, ".")
	} else {
		f, err := os.Open(args[0])
		if err != nil {
			return "", fmt.Errorf("Command failed. No such directory found: '%s'", args[0])
		}
		defer f.Close()
		st, err := f.Stat()
		if err != nil {
			return "", err
		}
		if st.Mode().IsRegular() {
			if !strings.HasSuffix(args[0], ".zip") {
				return "", fmt.Errorf("Application must be a zip file, was '%s'", args[0])
			}
			return uploadFile(opts, src, f, args[0])
		}
		if st.Mode().IsDir() {
			return uploadDirectory(opts, src, args[0])
		}
		return "", fmt.Errorf("Bad arg '%s' with FileMode %v", args[0], st.Mode())
	}
}

func uploadFrom(opts *Options, src string) (string, error) {
	url := src + pathPrefix(opts)
	url = addUrlPropertyFromOption(url, opts.From, "from")
	url = addUrlPropertyFromFlag(url, opts.Verbose, "verbose")
	trace.Trace("Upload from URL", opts.From, "using", urlWithoutQuery(url))
	output, err := curlPost(url, nil)
	return output, err
}

func uploadFile(opts *Options, src string, f *os.File, fileName string) (string, error) {
	url := src + pathPrefix(opts)
	url = addUrlPropertyFromFlag(url, opts.Verbose, "verbose")
	fmt.Printf("Uploading application '%s' using %s\n", fileName, urlWithoutQuery(url))
	output, err := curlPostZip(url, f)
	return output, err
}

func uploadDirectory(opts *Options, src string, dirName string) (string, error) {
	url := src + pathPrefix(opts)
	url = addUrlPropertyFromFlag(url, opts.Verbose, "verbose")
	fmt.Printf("Uploading application '%s' using %s\n", dirName, urlWithoutQuery(url))
	tarCmd := tarCommand(dirName)
	pipe, err := tarCmd.StdoutPipe()
	if err != nil {
		return "", err
	}
	err = tarCmd.Start()
	if err != nil {
		return "", err
	}
	output, err := curlPost(url, pipe)
	tarCmd.Wait()
	return output, err
}

func tarCommand(dirName string) *exec.Cmd {
	args := []string{
		"-C", dirName,
		"--dereference",
		"--exclude=.[a-zA-Z0-9]*",
		"--exclude=ext",
		"-czf", "-",
		".",
	}
	return exec.Command("tar", args...)
}
