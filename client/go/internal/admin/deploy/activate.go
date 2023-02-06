// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"fmt"
	"strconv"
)

// main entry point for vespa-deploy activate

func RunActivate(opts *Options, args []string) error {
	var sessId string
	if len(args) == 0 {
		sessId = getSessionIdFromFile(opts.Tenant)
	} else {
		sessId = args[0]
	}
	src := makeConfigsourceUrl(opts)
	url := src + pathPrefix(opts) + "/" + sessId + "/active"
	url = addUrlPropertyFromFlag(url, opts.Verbose, "verbose")
	url = addUrlPropertyFromOption(url, strconv.Itoa(opts.Timeout), "timeout")
	fmt.Printf("Activating session %s using %s\n", sessId, urlWithoutQuery(url))
	output, err := curlPutNothing(url)
	if err != nil {
		return err
	}
	var result ActivateResult
	code, err := decodeResponse(output, &result)
	if err != nil {
		return err
	}
	if code == 200 {
		fmt.Println(result.Message)
		fmt.Println("Checksum:  ", result.Application.Checksum)
		fmt.Println("Timestamp: ", result.Deploy.Timestamp)
		fmt.Println("Generation:", result.Application.Generation)
	} else {
		err = fmt.Errorf("Request failed. HTTP status code: %d\n%s", code, result.Message)
	}
	return err
}
