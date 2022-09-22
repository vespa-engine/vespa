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
	levelNone outputLevel = iota
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

func outputStderr(l outputLevel, v ...interface{}) {
	if l > currentOutputLevel {
		return
	}
	fmt.Fprintln(os.Stderr, v...)
}

func Info(v ...interface{}) {
	outputStderr(levelInfo, v...)
}

func Trace(v ...interface{}) {
	outputStderr(levelTrace, v...)
}

func Debug(v ...interface{}) {
	outputStderr(levelDebug, v...)
}

func Warning(v ...interface{}) {
	fmt.Fprintln(os.Stderr, v...)
}
