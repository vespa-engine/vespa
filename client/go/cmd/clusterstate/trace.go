// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

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

func outputStderr(l outputLevel, v ...interface{}) {
	if l > currentOutputLevel {
		return
	}
	fmt.Fprintln(os.Stderr, v...)
}

func PutInfo(v ...interface{}) {
	outputStderr(levelInfo, v...)
}

func PutTrace(v ...interface{}) {
	outputStderr(levelTrace, v...)
}

func PutDebug(v ...interface{}) {
	outputStderr(levelDebug, v...)
}

func PutWarning(v ...interface{}) {
	fmt.Fprintln(os.Stderr, v...)
}
