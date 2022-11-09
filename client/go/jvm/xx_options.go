// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"github.com/vespa-engine/vespa/client/go/defaults"
)

func (opts *Options) AddCommonXX() {
	crashDir := defaults.UnderVespaHome("var/crash")
	errorFile := crashDir + "/hs_err_pid%p.log"
	opts.fixSpec.FixDir(crashDir)
	opts.AddOption("-XX:+PreserveFramePointer")
	opts.AddOption("-XX:+HeapDumpOnOutOfMemoryError")
	opts.AddOption("-XX:HeapDumpPath=" + crashDir)
	opts.AddOption("-XX:ErrorFile=" + errorFile)
	opts.AddOption("-XX:+ExitOnOutOfMemoryError")
	// not common after all:
	opts.AddOption("-XX:MaxJavaStackTraceDepth=1000000")
}
