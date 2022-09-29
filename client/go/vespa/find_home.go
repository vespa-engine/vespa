// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// get or find VESPA_HOME
// Author: arnej

package vespa

import (
	"fmt"
	"os"
	"strings"
)

func FindHome() string {
	const (
		defaultInstallDir = "opt/vespa"
		commonEnvSh       = "libexec/vespa/common-env.sh"
	)
	// use env var if it is set:
	if ev := os.Getenv("VESPA_HOME"); ev != "" {
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
	var isFile = func(fn string) bool {
		st, err := os.Stat(fn)
		return err == nil && st.Mode().IsRegular()
	}
	var findPath = func() string {
		myProgName := os.Args[0]
		if strings.HasPrefix(myProgName, "/") {
			return dirName(myProgName)
		}
		if strings.Contains(myProgName, "/") {
			dir, _ := os.Getwd()
			return dir + "/" + dirName(myProgName)
		}
		for _, dir := range strings.Split(os.Getenv("PATH"), ":") {
			fn := dir + "/" + myProgName
			if isFile(fn) {
				return dir
			}
		}
		return ""
	}
	// detect path from argv[0]
	for path := findPath(); path != ""; path = dirName(path) {
		if isFile(path + "/" + commonEnvSh) {
			os.Setenv("VESPA_HOME", path)
			return path
		}
	}
	// fallback
	os.Setenv("VESPA_HOME", defaultInstallDir)
	return defaultInstallDir
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
