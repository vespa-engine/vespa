// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// handling of informational output
package trace

import (
	"fmt"
)

type outputLevel int

const (
	levelError outputLevel = iota - 2
	levelWarning
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

func outputTracing(l outputLevel, v ...interface{}) {
	if l > currentOutputLevel {
		return
	}
	msg := fmt.Sprintln(v...)
	logMessage(l, msg)
}

func Info(v ...interface{}) {
	outputTracing(levelInfo, v...)
}

func Trace(v ...interface{}) {
	outputTracing(levelTrace, v...)
}

func Debug(v ...interface{}) {
	outputTracing(levelDebug, v...)
}

func SpamDebug(v ...interface{}) {
	outputTracing(levelSpam, v...)
}

func Warning(v ...interface{}) {
	outputTracing(levelWarning, v...)
}
