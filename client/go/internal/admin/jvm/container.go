// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"sort"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/list"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
)

type Container interface {
	ServiceName() string
	ConfigId() string
	ArgForMain() string
	JvmOptions() *Options
	Exec()
	exportExtraEnv(ps *prog.Spec)
}

type containerBase struct {
	configId    string
	serviceName string
	jvmOpts     *Options
	propsFile   string
}

func (cb *containerBase) ServiceName() string {
	return cb.serviceName
}

func (cb *containerBase) JvmOptions() *Options {
	return cb.jvmOpts
}

func (cb *containerBase) ConfigId() string {
	return cb.configId
}

func keysOfMap(m map[string]string) []string {
	keys := make([]string, 0, len(m))
	for k, _ := range m {
		keys = append(keys, k)
	}
	return keys
}

func readableEnv(env map[string]string) string {
	keys := keysOfMap(env)
	sort.Strings(keys)
	var buf strings.Builder
	for _, k := range keys {
		fmt.Fprintf(&buf, " %s=%s", k, env[k])
	}
	return buf.String()
}

func (cb *containerBase) Exec() {
	argv := list.ArrayListOf(cb.JvmOptions().Args())
	argv.Insert(0, "java")
	p := prog.NewSpec(argv)
	p.ConfigureNumaCtl()
	cb.JvmOptions().exportEnvSettings(p)
	if cb.propsFile != "" {
		writeEnvAsProperties(p.EffectiveEnv(), cb.propsFile)
	}
	trace.Info("JVM env:", readableEnv(p.Env))
	trace.Info("JVM exec:", argv)
	err := p.Run()
	osutil.ExitErr(err)
}
