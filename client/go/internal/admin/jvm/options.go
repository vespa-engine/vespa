// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/list"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

type Options struct {
	container   Container
	classPath   []string
	jvmArgs     list.ArrayList[string]
	mainClass   string
	jarWithDeps string
	fixSpec     osutil.FixSpec
}

func NewOptions(c Container) *Options {
	vespaUid, vespaGid := vespa.FindVespaUidAndGid()
	fixSpec := osutil.FixSpec{
		UserId:   vespaUid,
		GroupId:  vespaGid,
		DirMode:  0755,
		FileMode: 0644,
	}
	return &Options{
		container:   c,
		classPath:   make([]string, 0, 10),
		jvmArgs:     make([]string, 0, 100),
		jarWithDeps: DEFAULT_JAR_WITH_DEPS,
		mainClass:   DEFAULT_MAIN_CLASS,
		fixSpec:     fixSpec,
	}
}

func (opts *Options) AddOption(arg string) {
	if opts.jvmArgs.Contains(arg) {
		return
	}
	opts.AppendOption(arg)
}

func (opts *Options) AppendOption(arg string) {
	trace.Trace("append JVM option:", arg)
	opts.jvmArgs = append(opts.jvmArgs, arg)
}

func (opts *Options) ClassPath() string {
	cp := defaults.UnderVespaHome(opts.jarWithDeps)
	for _, x := range opts.classPath {
		cp = fmt.Sprintf("%s:%s", cp, x)
	}
	trace.Trace("computed classpath:", cp)
	return cp
}

func (opts *Options) Args() []string {
	args := opts.jvmArgs
	args = append(args, "-cp")
	args = append(args, opts.ClassPath())
	args = append(args, opts.mainClass)
	args = append(args, opts.container.ArgForMain())
	return args
}

func (opts *Options) AddJvmArgsFromString(args string) {
	for _, x := range strings.Fields(args) {
		opts.AppendOption(x)
	}
}

func (opts *Options) ConfigureCpuCount(cnt int) {
	if cnt <= 0 {
		out, err := osutil.BackTicksForwardStderr.Run("nproc", "--all")
		if err != nil {
			trace.Trace("failed nproc:", err)
		} else {
			cnt, err = strconv.Atoi(strings.TrimSpace(out))
			if err != nil {
				trace.Trace("bad nproc output:", strings.TrimSpace(out))
				cnt = 0
			} else {
				trace.Trace("CpuCount: using", cnt, "from nproc --all")
			}
		}
	}
	if cnt > 0 {
		opts.AddOption(fmt.Sprintf("-XX:ActiveProcessorCount=%d", cnt))
	}
}
