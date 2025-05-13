// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// get or find VESPA_HOME
// Author: arnej

package vespa

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
)

const (
	defaultVespaInstallDir = "/opt/vespa"
	scriptUtilsFilename    = "libexec/vespa/vespa-wrapper"
)

func FindHome() string {
	// use env var if it is set:
	if ev := os.Getenv(envvars.VESPA_HOME); ev != "" {
		return ev
	}
	// some helper functions...
	var dirName = func(path string) string {
		idx := strings.LastIndex(path, "/")
		if idx < 0 {
			return ""
		}
		return path[:idx]
	}
	var findPath = func() string {
		myProgName := os.Args[0]
		if strings.HasPrefix(myProgName, "/") {
			trace.Debug("findPath", myProgName, "=>", dirName(myProgName))
			return dirName(myProgName)
		}
		if strings.Contains(myProgName, "/") {
			curDir, _ := os.Getwd()
			path := fmt.Sprintf("%s/%s", curDir, dirName(myProgName))
			trace.Debug("findPath", myProgName, "=>", path)
			return path
		}
		for _, dir := range strings.Split(os.Getenv(envvars.PATH), ":") {
			fn := fmt.Sprintf("%s/%s", dir, myProgName)
			if ioutil.IsFile(fn) {
				trace.Debug("findPath", myProgName, "=>", dir)
				return dir
			}
		}
		return ""
	}
	// detect path from argv[0]
	for path := findPath(); path != ""; path = dirName(path) {
		mySelf := fmt.Sprintf("%s/%s", path, scriptUtilsFilename)
		if ioutil.IsFile(mySelf) {
			trace.Debug("found", mySelf, "VH =>", path)
			os.Setenv(envvars.VESPA_HOME, path)
			return path
		}
	}
	// fallback
	os.Setenv(envvars.VESPA_HOME, defaultVespaInstallDir)
	return defaultVespaInstallDir
}

func HasFileUnderVespaHome(fn string) (bool, string) {
	fileName := fmt.Sprintf("%s/%s", FindHome(), fn)
	file, err := os.Open(fileName)
	if file != nil {
		file.Close()
		if err == nil {
			return true, fileName
		}
	}
	return false, ""
}

func FindAndVerifyVespaHome() string {
	vespaHome := FindHome()
	myself := fmt.Sprintf("%s/%s", vespaHome, scriptUtilsFilename)
	if !ioutil.IsExecutable(myself) {
		trace.Warning("missing or bad file:", myself)
		osutil.ExitMsg("Not a valid VESPA_HOME: " + vespaHome)
	}
	return vespaHome
}
