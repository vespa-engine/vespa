// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"bytes"
	"crypto/md5"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
)

const (
	JAR_FOR_APPLICATION_CONTAINER = "container-disc-jar-with-dependencies.jar"
)

type ApplicationContainer struct {
	containerBase
}

func md5Hex(text string) string {
	hasher := md5.New()
	io.WriteString(hasher, text)
	hash := hasher.Sum(nil)
	return fmt.Sprintf("%x", hash)
}

func (a *ApplicationContainer) ArgForMain() string {
	dir := defaults.UnderVespaHome("lib/jars")
	return fmt.Sprintf("file:%s/%s", dir, JAR_FOR_APPLICATION_CONTAINER)
}

func (a *ApplicationContainer) Discriminator() string {
	cfgId := a.ConfigId()
	if cfgId != "" {
		trace.Trace("Discriminator: using md5 of", cfgId)
		return md5Hex(cfgId + "\n")
	}
	svcName := a.ServiceName()
	if svcName != "" {
		trace.Trace("Discriminator: using", svcName)
		return svcName
	}
	pid := os.Getpid()
	trace.Trace("Discriminator: using md5 of", pid)
	return md5Hex(fmt.Sprintf("%d", pid))
}

func (a *ApplicationContainer) addJdiscProperties() {
	cfgId := a.ConfigId()
	opts := a.jvmOpts
	opts.AddCommonJdiscProperties()
	containerParentDir := defaults.UnderVespaHome("var/jdisc_container")
	containerHomeDir := fmt.Sprintf("%s/%s", containerParentDir, a.Discriminator())
	bCacheDir := fmt.Sprintf("%s/%s", containerHomeDir, "bundlecache")
	propsFile := fmt.Sprintf("%s/%s.properties", containerHomeDir, "jdisc")
	opts.fixSpec.FixDir(containerHomeDir)
	opts.fixSpec.FixDir(bCacheDir)
	a.propsFile = propsFile
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
	opts := a.jvmOpts
	opts.AddOption(fmt.Sprintf("-Xms%dm", jvm_minHeapsize))
	opts.AddOption(fmt.Sprintf("-Xmx%dm", jvm_heapsize))
	if jvm_stacksize > 0 {
		opts.AddOption(fmt.Sprintf("-XX:ThreadStackSize=%d", jvm_stacksize))
	}
	if jvm_baseMaxDirectMemorySize > 0 {
		maxDirectMemorySize := jvm_baseMaxDirectMemorySize
		if jvm_directMemorySizeCache > 0 {
			maxDirectMemorySize += jvm_directMemorySizeCache
		}
		maxDirectMemorySize += jvm_heapsize / 8
		opts.AddOption(fmt.Sprintf("-XX:MaxDirectMemorySize=%dm", maxDirectMemorySize))
	}
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
	if env := os.Getenv(envvars.VESPA_CONTAINER_JVMARGS); env != "" {
		opts.AddJvmArgsFromString(env)
	}
	qrStartCfg := a.getQrStartCfg()
	opts.AddOption("-Djdisc.export.packages=" + qrStartCfg.Jdisc.ExportPackages)
	opts.AddCommonXX()
	opts.AddCommonOpens()
	opts.AddCommonJdkProperties()
	a.configureCPU(qrStartCfg)
	a.configureMemory(qrStartCfg)
	a.configureGC(qrStartCfg)
	a.configureClasspath(qrStartCfg)
	a.addJdkVersionSpecificArgs()
	a.addJdiscProperties()
	svcName := a.ServiceName()
	if svcName == "container" || svcName == "container-clustercontroller" {
		RemoveStaleZkLocks(a)
		logsDir := defaults.UnderVespaHome("logs/vespa")
		zkLogFile := fmt.Sprintf("%s/zookeeper.%s", logsDir, svcName)
		opts.AddOption("-Dzookeeper_log_file_prefix=" + zkLogFile)
	}
}

func (c *ApplicationContainer) exportExtraEnv(ps *prog.Spec) {
	if c.ConfigId() != "" {
		ps.Setenv(envvars.VESPA_CONFIG_ID, c.ConfigId())
	} else {
		osutil.ExitMsg("application container requires a config id")
	}
}

// addJdkVersionSpecificArgs appends JVM args depending on detected JDK major version.
// - JDK 17/18: --add-modules=jdk.incubator.foreign
// - JDK 19-21: --enable-preview and --enable-native-access=ALL-UNNAMED
func (a *ApplicationContainer) addJdkVersionSpecificArgs() {
	major := detectJavaMajorVersion()
	if major == 0 {
		trace.Warning("Could not detect Java version; skipping version-specific JVM args")
		return
	}
	switch {
	case major == 17 || major == 18:
		a.JvmOptions().AddOption("--add-modules=jdk.incubator.foreign")
		trace.Info("Added incubator module flag for Java", major)
	case major >= 19 && major <= 21:
		a.JvmOptions().AddOption("--enable-preview")
		a.JvmOptions().AddOption("--enable-native-access=ALL-UNNAMED")
		trace.Info("Added preview and native-access flags for Java", major)
	default:
		trace.Warning("Unrecognized Java major version, no additional JVM flags added:", major)
	}
}

// Returns 0 on failure.
func detectJavaMajorVersion() int {
	java := "java"
	if home := os.Getenv("JAVA_HOME"); home != "" {
		candidate := filepath.Join(home, "bin", "java")
		if _, err := os.Stat(candidate); err == nil {
			java = candidate
		}
	}
	cmd := exec.Command(java, "-version")
	var out bytes.Buffer
	var errorBuffer bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errorBuffer
	if err := cmd.Run(); err != nil {
		trace.Trace("java -version failed:", err)
		return 0
	}
	s := out.String()
	if s == "" {
		s = errorBuffer.String()
	}
	// Look for a quoted version like "17.0.9"
	re := regexp.MustCompile(`\"(\d+)(?:\.(\d+))?`)
	m := re.FindStringSubmatch(s)
	if len(m) < 2 {
		return 0
	}
	major, _ := strconv.Atoi(m[1])
	// Handle legacy "1.x" formats (e.g. Java 8 -> "1.8")
	if major == 1 && len(m) >= 3 {
		if minor, err := strconv.Atoi(m[2]); err == nil {
			return minor
		}
	}
	return major
}
