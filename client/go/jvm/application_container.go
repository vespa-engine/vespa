// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/defaults"
	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	JAR_FOR_APPLICATION_CONTAINER = "container-disc-jar-with-dependencies.jar"
)

type ApplicationContainer struct {
	containerBase
}

func (a *ApplicationContainer) ArgForMain() string {
	dir := defaults.UnderVespaHome("lib/jars")
	return fmt.Sprintf("file:%s/%s", dir, JAR_FOR_APPLICATION_CONTAINER)
}

func (a *ApplicationContainer) Discriminator() string {
	cfgId := a.ConfigId()
	if cfgId != "" {
		trace.Trace("Discriminator: using md5 of", cfgId)
		return util.Md5Hex(cfgId + "\n")
	}
	svcName := a.ServiceName()
	if svcName != "" {
		trace.Trace("Discriminator: using", svcName)
		return svcName
	}
	pid := os.Getpid()
	trace.Trace("Discriminator: using md5 of", pid)
	return util.Md5Hex(fmt.Sprintf("%d", pid))
}

func (a *ApplicationContainer) addJdiscProperties() {
	cfgId := a.ConfigId()
	opts := a.jvmArgs
	opts.AddCommonJdiscProperties()
	containerParentDir := defaults.UnderVespaHome("var/jdisc_container")
	containerHomeDir := fmt.Sprintf("%s/%s", containerParentDir, a.Discriminator())
	bCacheDir := fmt.Sprintf("%s/%s", containerHomeDir, "bundlecache")
	propsFile := fmt.Sprintf("%s/%s.properties", containerHomeDir, "jdisc")
	opts.fixSpec.FixDir(containerHomeDir)
	opts.fixSpec.FixDir(bCacheDir)
	trace.Trace("write props file:", propsFile)
	err := os.WriteFile(propsFile, selectedEnv(), 0600)
	if err != nil {
		util.JustExitWith(err)
	}
	opts.AddOption("-Djdisc.config.file=" + propsFile)
	opts.AddOption("-Djdisc.cache.path=" + bCacheDir)
	opts.AddOption("-Djdisc.logger.tag=" + cfgId)
}

func validPercentage(val int) bool {
	return val > 0 && val < 100
}

func (a *ApplicationContainer) configureMemory(qc *QrStartConfig) {
	jvm_heapsize := qc.Jvm.Heapsize                                                         // Heap size (in megabytes) for the Java VM
	jvm_minHeapsize := qc.Jvm.MinHeapsize                                                   // Min heapsize (in megabytes) for the Java VM
	jvm_stacksize := qc.Jvm.Stacksize                                                       // Stack size (in kilobytes)
	jvm_compressedClassSpaceSize := qc.Jvm.CompressedClassSpaceSize                         // CompressedOOps size in megabytes
	jvm_baseMaxDirectMemorySize := qc.Jvm.BaseMaxDirectMemorySize                           // Base value of maximum direct memory size (in megabytes)
	jvm_directMemorySizeCache := qc.Jvm.DirectMemorySizeCache                               // Amount of direct memory used for caching. (in megabytes)
	jvm_heapSizeAsPercentageOfPhysicalMemory := qc.Jvm.HeapSizeAsPercentageOfPhysicalMemory // Heap size as percentage of available RAM, overrides value above.

	if jvm_heapsize <= 0 {
		jvm_heapsize = 1536
		trace.Trace("using hardcoded value for jvm_heapsize:", jvm_heapsize)
	}
	if jvm_minHeapsize <= 0 {
		jvm_minHeapsize = jvm_heapsize
	}
	available := getAvailableMemory()
	if validPercentage(jvm_heapSizeAsPercentageOfPhysicalMemory) && available.ToMB() > 500 {
		available = adjustAvailableMemory(available)
		jvm_heapsize = available.ToMB() * jvm_heapSizeAsPercentageOfPhysicalMemory / 100
		jvm_minHeapsize = jvm_heapsize
	}
	if jvm_minHeapsize > jvm_heapsize {
		trace.Warning(fmt.Sprintf(
			"Misconfigured heap size, jvm_minHeapsize(%d) is larger than jvm_heapsize(%d). It has been capped.",
			jvm_minHeapsize, jvm_heapsize))
		jvm_minHeapsize = jvm_heapsize
	}
	if jvm_stacksize <= 0 {
		jvm_stacksize = 512
	}
	if jvm_baseMaxDirectMemorySize <= 0 {
		jvm_baseMaxDirectMemorySize = 75
	}
	if jvm_compressedClassSpaceSize < 0 {
		jvm_compressedClassSpaceSize = 32
	}
	if jvm_directMemorySizeCache < 0 {
		jvm_directMemorySizeCache = 0
	}
	maxDirectMemorySize := jvm_baseMaxDirectMemorySize + (jvm_heapsize / 8) + jvm_directMemorySizeCache
	opts := a.jvmArgs
	opts.AddOption(fmt.Sprintf("-Xms%dm", jvm_minHeapsize))
	opts.AddOption(fmt.Sprintf("-Xmx%dm", jvm_heapsize))
	opts.AddOption(fmt.Sprintf("-XX:ThreadStackSize=%d", jvm_stacksize))
	opts.AddOption(fmt.Sprintf("-XX:MaxDirectMemorySize=%dm", maxDirectMemorySize))
	opts.MaybeAddHugepages(MegaBytesOfMemory(jvm_heapsize))
	if jvm_compressedClassSpaceSize > 0 {
		opts.AddOption(fmt.Sprintf("-XX:CompressedClassSpaceSize=%dm", jvm_compressedClassSpaceSize))
	}
}

func (a *ApplicationContainer) configureGC(qc *QrStartConfig) {
	if extra := qc.Jvm.Gcopts; extra != "" {
		a.JvmOptions().AddJvmArgsFromString(extra)
	}
	if qc.Jvm.Verbosegc {
		a.JvmOptions().AddOption("-Xlog:gc")
	}
}

func (a *ApplicationContainer) configureClasspath(qc *QrStartConfig) {
	opts := a.JvmOptions()
	if cp := qc.Jdisc.ClasspathExtra; cp != "" {
		opts.classPath = append(opts.classPath, cp)
	}
}

func (a *ApplicationContainer) configureCPU(qc *QrStartConfig) {
	cnt := qc.Jvm.AvailableProcessors
	if cnt > 0 {
		trace.Trace("CpuCount: using", cnt, "from qr-start config")
	}
	a.JvmOptions().ConfigureCpuCount(cnt)
}

func (a *ApplicationContainer) configureOptions() {
	opts := a.JvmOptions()
	opts.AddOption("-Dconfig.id=" + a.ConfigId())
	if env := os.Getenv(VESPA_CONTAINER_JVMARGS); env != "" {
		opts.AddJvmArgsFromString(env)
	}
	qrStartCfg := a.getQrStartCfg()
	a.configureCPU(qrStartCfg)
	a.configureMemory(qrStartCfg)
	a.configureGC(qrStartCfg)
	a.configureClasspath(qrStartCfg)
	opts.AddOption("-Djdisc.export.packages=" + qrStartCfg.Jdisc.ExportPackages)
	opts.AddCommonXX()
	opts.AddCommonOpens()
	opts.AddCommonJdkProperties()
	a.addJdiscProperties()
	svcName := a.ServiceName()
	if svcName == "container" || svcName == "container-clustercontroller" {
		RemoveStaleZkLocks(a)
		logsDir := defaults.UnderVespaHome("logs/vespa")
		zkLogFile := fmt.Sprintf("%s/zookeeper.%s", logsDir, svcName)
		opts.AddOption("-Dzookeeper_log_file_prefix=" + zkLogFile)
	}
}
