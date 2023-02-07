// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"strings"
)

// is componentName a vespa-internal name?

func isInternal(componentName string) bool {
	cs := strings.Split(componentName, ".")
	if len(cs) == 0 || cs[0] != "Container" {
		return true
	}
	if len(cs) < 3 {
		return false
	}
	if cs[1] == "ai" && cs[2] == "vespa" {
		return true
	}
	if cs[1] == "com" && cs[2] == "yahoo" && len(cs) > 3 {
		return internalComYahooNames[cs[3]]
	}
	return false
}

// a constant:
var internalComYahooNames = map[string]bool{
	"abicheck":              true,
	"api":                   true,
	"application":           true,
	"binaryprefix":          true,
	"clientmetrics":         true,
	"cloud":                 true,
	"collections":           true,
	"component":             true,
	"compress":              true,
	"concurrent":            true,
	"configtest":            true,
	"config":                true,
	"container":             true,
	"data":                  true,
	"docprocs":              true,
	"docproc":               true,
	"documentapi":           true,
	"documentmodel":         true,
	"document":              true,
	"dummyreceiver":         true,
	"embedding":             true,
	"errorhandling":         true,
	"exception":             true,
	"feedapi":               true,
	"feedhandler":           true,
	"filedistribution":      true,
	"fs4":                   true,
	"fsa":                   true,
	"geo":                   true,
	"io":                    true,
	"javacc":                true,
	"jdisc":                 true,
	"jrt":                   true,
	"lang":                  true,
	"language":              true,
	"logserver":             true,
	"log":                   true,
	"messagebus":            true,
	"metrics":               true,
	"nativec":               true,
	"net":                   true,
	"osgi":                  true,
	"path":                  true,
	"plugin":                true,
	"prelude":               true,
	"processing":            true,
	"protect":               true,
	"reflection":            true,
	"restapi":               true,
	"schema":                true,
	"searchdefinition":      true,
	"searchlib":             true,
	"search":                true,
	"security":              true,
	"slime":                 true,
	"socket":                true,
	"statistics":            true,
	"stream":                true,
	"system":                true,
	"tensor":                true,
	"test":                  true,
	"text":                  true,
	"time":                  true,
	"transaction":           true,
	"vdslib":                true,
	"vespaclient":           true,
	"vespafeeder":           true,
	"vespaget":              true,
	"vespastat":             true,
	"vespasummarybenchmark": true,
	"vespa":                 true,
	"vespavisit":            true,
	"vespaxmlparser":        true,
	"yolean":                true,
}
