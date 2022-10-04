// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package startcbinary

import (
	"fmt"
	"os"
	"runtime"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/trace"
)

func setup(t *testing.T, testFileName string) {
	trace.AdjustVerbosity(1)
	mockBinParent = strings.TrimSuffix(testFileName, "/numactl_test.go")
	tmpBin = t.TempDir() + "/mock.bin.numactl_test"
	err := os.MkdirAll(tmpBin, 0755)
	assert.Nil(t, err)
	t.Setenv("PATH", fmt.Sprintf("%s:%s", tmpBin, os.Getenv("PATH")))
}

func TestNumaCtlDetection(t *testing.T) {
	if runtime.GOOS == "windows" {
		return
	}
	_, tfn, _, _ := runtime.Caller(0)
	setup(t, tfn)
	orig := []string{"/bin/myprog", "-c", "cfgid"}
	spec := NewProgSpec(orig)

	useMock("no-numactl", "numactl")
	spec.configureNumaCtl()
	assert.Equal(t, false, spec.shouldUseNumaCtl)

	useMock("bad-numactl", "numactl")
	spec.configureNumaCtl()
	assert.Equal(t, false, spec.shouldUseNumaCtl)

	t.Setenv("VESPA_AFFINITY_CPU_SOCKET", "")
	useMock("good-numactl", "numactl")
	spec.configureNumaCtl()
	assert.Equal(t, true, spec.shouldUseNumaCtl)
	assert.Equal(t, -1, spec.numaSocket)
	argv := spec.prependNumaCtl(orig)
	trace.Trace("argv:", argv)
	assert.Equal(t, 6, len(argv))
	assert.Equal(t, "numactl", argv[0])
	assert.Equal(t, "--interleave", argv[1])
	assert.Equal(t, "all", argv[2])
	assert.Equal(t, "/bin/myprog-bin", argv[3])
	assert.Equal(t, "-c", argv[4])
	assert.Equal(t, "cfgid", argv[5])

	t.Setenv("VESPA_AFFINITY_CPU_SOCKET", "0")
	spec.configureNumaCtl()
	assert.Equal(t, true, spec.shouldUseNumaCtl)
	assert.Equal(t, 0, spec.numaSocket)
	argv = spec.prependNumaCtl(orig)
	trace.Trace("argv:", argv)
	assert.Equal(t, 6, len(argv))
	assert.Equal(t, "numactl", argv[0])
	assert.Equal(t, "--cpunodebind=0", argv[1])
	assert.Equal(t, "--membind=0", argv[2])
	assert.Equal(t, "/bin/myprog-bin", argv[3])
	assert.Equal(t, "-c", argv[4])
	assert.Equal(t, "cfgid", argv[5])

	t.Setenv("VESPA_AFFINITY_CPU_SOCKET", "1")
	spec.configureNumaCtl()
	assert.Equal(t, true, spec.shouldUseNumaCtl)
	assert.Equal(t, 1, spec.numaSocket)
	argv = spec.prependNumaCtl(orig)
	trace.Trace("argv:", argv)
	assert.Equal(t, 6, len(argv))
	assert.Equal(t, "numactl", argv[0])
	assert.Equal(t, "--cpunodebind=1", argv[1])
	assert.Equal(t, "--membind=1", argv[2])
	assert.Equal(t, "/bin/myprog-bin", argv[3])
	assert.Equal(t, "-c", argv[4])
	assert.Equal(t, "cfgid", argv[5])

	t.Setenv("VESPA_AFFINITY_CPU_SOCKET", "2")
	spec.configureNumaCtl()
	assert.Equal(t, true, spec.shouldUseNumaCtl)
	assert.Equal(t, 0, spec.numaSocket)

}
