// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"fmt"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func makeConfigsourceUrl(opts *Options) string {
	src := makeConfigsourceUrls(opts)[0]
	if opts.Command == CmdPrepare || opts.Command == CmdActivate {
		if lastUsed := getConfigsourceUrlUsed(); lastUsed != "" {
			return lastUsed
		}
		fmt.Printf("Could not read config server URL used for previous upload of an application package, trying to use %s\n", src)
	}
	return src
}

func makeConfigsourceUrls(opts *Options) []string {
	var results = make([]string, 0, 3)
	if opts.ServerHost == "" {
		home := vespa.FindHome()
		backticks := util.BackTicksForwardStderr
		configsources, _ := backticks.Run(home+"/bin/vespa-print-default", "configservers_http")
		for _, src := range strings.Split(configsources, "\n") {
			colonParts := strings.Split(src, ":")
			if len(colonParts) > 1 {
				// XXX overwrites port number from above - is this sensible?
				src = fmt.Sprintf("%s:%s:%d", colonParts[0], colonParts[1], opts.PortNumber)
				trace.Trace("can use config server at", src)
				results = append(results, src)
			}
		}
		if len(results) == 0 {
			trace.Warning("Could not get url to config server, make sure that VESPA_CONFIGSERVERS is set")
			results = append(results, fmt.Sprintf("http://localhost:%d", opts.PortNumber))
		}
	} else {
		results = append(results, fmt.Sprintf("http://%s:%d", opts.ServerHost, opts.PortNumber))
	}
	return results
}

func pathPrefix(opts *Options) string {
	return "/application/v2/tenant/" + opts.Tenant + "/session"
}

func addUrlPropertyFromFlag(url string, flag bool, propName string) string {
	if !flag {
		return url
	} else {
		return addUrlPropertyFromOption(url, "true", propName)
	}
}

func addUrlPropertyFromOption(url, flag, propName string) string {
	if flag == "" {
		return url
	}
	if strings.Contains(url, "?") {
		return url + "&" + propName + "=" + flag
	} else {
		return url + "?" + propName + "=" + flag
	}
}
