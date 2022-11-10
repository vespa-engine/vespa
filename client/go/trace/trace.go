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

func outputStderr(l outputLevel, n string, v ...interface{}) {
	if l > currentOutputLevel {
		return
	}
	w := make([]interface{}, len(v)+1)
	w[0] = n
	for idx, arg := range v {
		w[idx+1] = arg
	}
	fmt.Fprintln(os.Stderr, w...)
}

func Info(v ...interface{}) {
	outputStderr(levelInfo, "[info]", v...)
}

func Trace(v ...interface{}) {
	outputStderr(levelTrace, "[trace]", v...)
}

func Debug(v ...interface{}) {
	outputStderr(levelDebug, "[debug]", v...)
}

func Warning(v ...interface{}) {
	outputStderr(levelWarning, "[warning]", v...)
}
