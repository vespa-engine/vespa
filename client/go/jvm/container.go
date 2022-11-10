// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"github.com/vespa-engine/vespa/client/go/prog"
	"github.com/vespa-engine/vespa/client/go/util"
)

type Container interface {
	ServiceName() string
	ConfigId() string
	ArgForMain() string
	JvmOptions() *Options
	Exec()
}

type containerBase struct {
	configId    string
	serviceName string
	jvmArgs     *Options
}

func (cb *containerBase) ServiceName() string {
	return cb.serviceName
}

func (cb *containerBase) JvmOptions() *Options {
	return cb.jvmArgs
}

func (cb *containerBase) ConfigId() string {
	return cb.configId
}

func (cb *containerBase) Exec() {
	argv := make([]string, 0, 100)
	argv = append(argv, "java")
	for _, x := range cb.JvmOptions().Args() {
		argv = append(argv, x)
	}
	p := prog.NewSpec(argv)
	p.ConfigureNumaCtl()
	exportEnvSettings(cb, p)
	err := p.Run()
	util.JustExitWith(err)
}
