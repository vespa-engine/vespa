// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/list"
)

func RunJava(args []string) error {
	if doTrace := os.Getenv(envvars.TRACE_JVM_STARTUP); doTrace != "" {
		trace.AdjustVerbosity(1)
	}
	if doDebug := os.Getenv(envvars.DEBUG_JVM_STARTUP); doDebug != "" {
		trace.AdjustVerbosity(2)
	}
	var opts = &Options{
		jvmArgs: make([]string, 0, 100),
	}
	opts.VersionOptions(NoExtraJvmFeatures)
	opts.AddOption("-Djava.io.tmpdir=" + defaults.UnderVespaHome("var/tmp"))
	opts.AddCommonOpens()
	// opts -> argv
	argv := list.ArrayListOf(opts.Args())
	argv.Insert(0, "java")
	argv.AppendAll(args...)
	// argv -> program spec -> run it
	p := prog.NewSpec(argv)
	trace.Trace("JVM exec:", argv)
	return p.Run()
}
