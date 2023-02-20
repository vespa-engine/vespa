// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
)

const (
	PROXY_JAR_FILE   = "lib/jars/config-proxy-jar-with-dependencies.jar"
	PROXY_MAIN_CLASS = "com.yahoo.vespa.config.proxy.ProxyServer"
	PROXY_PORT_ARG   = "19090"
)

type ConfigProxyJvm struct {
	containerBase
}

func (cpc *ConfigProxyJvm) ArgForMain() string {
	return PROXY_PORT_ARG
}

func (cpc *ConfigProxyJvm) ConfigId() string {
	return ""
}

func (cpc *ConfigProxyJvm) ConfigureOptions(configsources []string, userargs string) {
	opts := cpc.jvmOpts
	opts.jarWithDeps = PROXY_JAR_FILE
	opts.mainClass = PROXY_MAIN_CLASS
	opts.AddOption("-XX:+ExitOnOutOfMemoryError")
	opts.AddOption("-XX:+PreserveFramePointer")
	opts.AddOption("-XX:CompressedClassSpaceSize=32m")
	opts.AddOption("-XX:MaxDirectMemorySize=32m")
	opts.AddOption("-XX:ThreadStackSize=448")
	opts.AddOption("-XX:MaxJavaStackTraceDepth=1000")
	opts.AddOption("-XX:-OmitStackTraceInFastThrow")
	opts.AddOption("-XX:ActiveProcessorCount=2")
	opts.AddOption("-Dproxyconfigsources=" + strings.Join(configsources, ","))
	opts.AddOption("-Djava.io.tmpdir=${VESPA_HOME}/var/tmp")
	if userargs != "" {
		opts.AddJvmArgsFromString(userargs)
	}
	minFallback := MegaBytesOfMemory(32)
	maxFallback := MegaBytesOfMemory(128)
	opts.AddDefaultHeapSizeArgs(minFallback, maxFallback)
}

func (cpc *ConfigProxyJvm) exportExtraEnv(ps *prog.Spec) {
}

func NewConfigProxyJvm(serviceName string) *ConfigProxyJvm {
	var cpc ConfigProxyJvm
	cpc.serviceName = serviceName
	cpc.jvmOpts = NewOptions(&cpc)
	return &cpc
}
