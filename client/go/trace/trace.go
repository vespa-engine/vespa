// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package trace

import (
	"fmt"
	"os"
)

// handling of informational output

type outputLevel int

const (
	levelWarning outputLevel = iota - 1
	levelNone
	levelInfo
	levelTrace
	levelDebug
	levelSpam
)

var currentOutputLevel outputLevel = levelInfo

func AdjustVerbosity(howMuch int) {
	currentOutputLevel = (outputLevel)(howMuch + int(currentOutputLevel))
}

func Silent() {
	currentOutputLevel = levelNone
}

func outputTracing(l outputLevel, n string, v ...interface{}) {
	if l > currentOutputLevel {
		return
	}
	out := os.Stderr
	fmt.Fprintf(out, "%s\t", n)
	fmt.Fprintln(out, v...)
}

func Info(v ...interface{}) {
	outputTracing(levelInfo, "info", v...)
}

func Trace(v ...interface{}) {
	outputTracing(levelTrace, "info", v...)
}

func Debug(v ...interface{}) {
	outputTracing(levelDebug, "debug", v...)
}

func Warning(v ...interface{}) {
	outputTracing(levelWarning, "warning", v...)
}
