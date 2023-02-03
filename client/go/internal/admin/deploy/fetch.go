// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/util"
)

// main entry point for vespa-deploy fetch

func RunFetch(opts *Options, args []string) error {
	dirName := "."
	if len(args) > 0 {
		dirName = args[0]
	}
	src := makeConfigsourceUrl(opts)
	url := src +
		"/application/v2" +
		"/tenant/" + opts.Tenant +
		"/application/" + opts.Application +
		"/environment/" + opts.Environment +
		"/region/" + opts.Region +
		"/instance/" + opts.Instance +
		"/content/"

	url = addUrlPropertyFromOption(url, strconv.Itoa(opts.Timeout), "timeout")
	fmt.Printf("Writing active application to %s\n(using %s)\n", dirName, urlWithoutQuery(url))
	var out bytes.Buffer
	err := curlGet(url, &out)
	if err != nil {
		return err
	}
	fetchDirectory(dirName, &out)
	return err
}

func fetchDirectory(name string, input *bytes.Buffer) {
	err := os.MkdirAll(name, 0755)
	if err != nil {
		fmt.Printf("ERROR: %v\n", err)
		return
	}
	codec := json.NewDecoder(input)
	var result []string
	err = codec.Decode(&result)
	if err != nil {
		fmt.Printf("ERROR: %v [%v] <<< %s\n", result, err, input.String())
		return
	}
	for _, entry := range result {
		fmt.Println("GET", entry)
		fn := name + "/" + getPartAfterSlash(entry)
		if strings.HasSuffix(entry, "/") {
			var out bytes.Buffer
			err := curlGet(entry, &out)
			if err != nil {
				fmt.Println("FAILED", err)
				return
			}
			fetchDirectory(fn, &out)
		} else {
			f, err := os.Create(fn)
			if err != nil {
				fmt.Println("FAILED", err)
				return
			}
			defer f.Close()
			err = curlGet(entry, f)
			if err != nil {
				fmt.Println("FAILED", err)
				return
			}
		}
	}
}

func getPartAfterSlash(path string) string {
	parts := strings.Split(path, "/")
	idx := len(parts) - 1
	if idx > 1 && parts[idx] == "" {
		return parts[idx-1]
	}
	if idx == 0 {
		util.JustExitMsg("cannot find part after slash: " + path)
	}
	return parts[idx]
}
