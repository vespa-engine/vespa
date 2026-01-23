// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"bytes"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

type ExtraJvmFeatures int

// Define the possible values as constants
const (
	NoExtraJvmFeatures ExtraJvmFeatures = iota
	PreviewJvmFeatures
)

type DetectJavaVersion struct {
	output string
	major  int
}

// VersionOptions appends JVM args depending on detected JDK major version.
// - JDK 17/18: --add-modules=jdk.incubator.foreign
// - JDK 19-21: --enable-preview and --enable-native-access=ALL-UNNAMED
func (opts *Options) VersionOptions(extra ExtraJvmFeatures) {
	// Experimental: Retry if unable to get major version, as there is sometimes
	// an error message related to perf data locking
	i := 0
	javaVersion := DetectJavaVersion{output: "", major: 0}
	for {
		javaVersion = detectJavaMajorVersion()
		i++
		if javaVersion.major != 0 || i > 5 {
			break
		}
	}
	major := javaVersion.major
	if major == 0 {
		trace.Warning("Could not detect Java version; skipping version-specific JVM args. Output: " + javaVersion.output)
		return
	}
	switch {
	case major == 17 || major == 18:
		if extra != NoExtraJvmFeatures {
			opts.AddOption("--add-modules=jdk.incubator.foreign")
			trace.Debug("Added incubator module flag for Java", major)
		}
	case major >= 19 && major <= 21:
		if extra != NoExtraJvmFeatures {
			opts.AddOption("--enable-preview")
			opts.AddOption("--enable-native-access=ALL-UNNAMED")
			trace.Debug("Added preview and native-access flags for Java", major)
		} else {
			opts.AddOption("--enable-native-access=ALL-UNNAMED")
			trace.Debug("Added native-access flags for Java", major)
		}
	case major == 25:
		if runtime.GOARCH == "arm64" {
			opts.AddOption("-XX:UseSVE=0")
			opts.AddOption("--enable-native-access=ALL-UNNAMED")
			trace.Debug("Added SVE and native-access flags for Java", major)
		} else {
			opts.AddOption("--enable-native-access=ALL-UNNAMED")
			trace.Debug("Added native-access flags for Java", major)
		}
	default:
		trace.Warning("Unrecognized Java major version, no additional JVM flags added:", major)
	}
}

// Returns 0 on failure.
func detectJavaMajorVersion() DetectJavaVersion {
	java := "java"
	if home := os.Getenv("JAVA_HOME"); home != "" {
		candidate := filepath.Join(home, "bin", "java")
		if _, err := os.Stat(candidate); err == nil {
			java = candidate
		}
	}
	// The UsePerfData option is added to avoid issues with perf data path being locked
	cmd := exec.Command(java, "-version", "-XX:-UsePerfData")
	var out bytes.Buffer
	var errorBuffer bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errorBuffer
	if err := cmd.Run(); err != nil {
		trace.Trace("java -version failed:", err)
		return DetectJavaVersion{output: out.String(), major: 0}
	}
	s := out.String()
	if s == "" {
		s = errorBuffer.String()
	}
	return DetectJavaVersion{output: s, major: parseJavaVersion(s)}
}

func parseJavaVersion(versionString string) int {
	// Look for a quoted version like "17.0.9"
	re := regexp.MustCompile(`\"(\d+)(?:\.(\d+))?`)
	m := re.FindStringSubmatch(versionString)
	if len(m) < 2 {
		return 0
	}
	major, _ := strconv.Atoi(m[1])
	// Handle legacy "1.x" formats (e.g. Java 8 -> "1.8")
	if major == 1 && len(m) >= 3 {
		if minor, err := strconv.Atoi(m[2]); err == nil {
			return minor
		}
	}
	return major
}
