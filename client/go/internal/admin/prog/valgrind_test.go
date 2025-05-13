// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package prog

import (
	"fmt"
	"os"
	"runtime"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
)

var tmpBin string
var mockBinParent string

func useMock(prog, target string) {
	mock := fmt.Sprintf("%s/mockbin/%s", mockBinParent, prog)
	symlink := fmt.Sprintf("%s/%s", tmpBin, target)
	os.Remove(symlink)
	err := os.Symlink(mock, symlink)
	if err != nil {
		osutil.ExitErr(err)
	}
}

func setupValgrind(t *testing.T, testFileName string) {
	trace.AdjustVerbosity(1)
	t.Setenv("VESPA_HOME", mockBinParent+"/mock_vespahome")
	mockBinParent = strings.TrimSuffix(testFileName, "/valgrind_test.go")
	tmpBin = t.TempDir() + "/mock.bin.valgrind_test"
	err := os.MkdirAll(tmpBin, 0755)
	assert.Nil(t, err)
	t.Setenv("PATH", fmt.Sprintf("%s:%s", tmpBin, os.Getenv("PATH")))
}

func TestValgrindDetection(t *testing.T) {
	if runtime.GOOS == "windows" {
		return
	}
	_, tfn, _, _ := runtime.Caller(0)
	setupValgrind(t, tfn)
	spec := NewSpec([]string{"/opt/vespa/bin/foobar"})
	var argv []string

	useMock("has-valgrind", VALGRIND_PROG)

	t.Setenv("VESPA_USE_VALGRIND", "")
	spec.ConfigureValgrind()
	assert.Equal(t, false, spec.shouldUseValgrind)
	assert.Equal(t, false, spec.shouldUseCallgrind)

	t.Setenv("VESPA_USE_VALGRIND", "all")
	spec.ConfigureValgrind()
	assert.Equal(t, true, spec.shouldUseValgrind)
	assert.Equal(t, false, spec.shouldUseCallgrind)

	t.Setenv("VESPA_USE_VALGRIND", "foo bar")
	spec.ConfigureValgrind()
	assert.Equal(t, false, spec.shouldUseValgrind)
	assert.Equal(t, false, spec.shouldUseCallgrind)

	t.Setenv("VESPA_USE_VALGRIND", "foobar")
	spec.ConfigureValgrind()
	assert.Equal(t, true, spec.shouldUseValgrind)
	assert.Equal(t, false, spec.shouldUseCallgrind)

	argv = spec.prependValgrind([]string{"/bin/myprog", "-c", "cfgid"})
	trace.Trace("argv:", argv)
	assert.Equal(t, 11, len(argv))
	assert.Equal(t, "valgrind", argv[0])
	assert.Equal(t, "/bin/myprog", argv[8])

	t.Setenv("VESPA_USE_VALGRIND", "another foobar yetmore")
	spec.ConfigureValgrind()
	assert.Equal(t, true, spec.shouldUseValgrind)
	assert.Equal(t, false, spec.shouldUseCallgrind)

	t.Setenv("VESPA_VALGRIND_OPT", "--tool=callgrind")
	spec.ConfigureValgrind()
	assert.Equal(t, true, spec.shouldUseValgrind)
	assert.Equal(t, true, spec.shouldUseCallgrind)

	argv = spec.prependValgrind([]string{"/bin/myprog", "-c", "cfgid"})
	trace.Trace("argv:", argv)
	assert.Equal(t, 6, len(argv))
	assert.Equal(t, "valgrind", argv[0])
	assert.Equal(t, "--tool=callgrind", argv[1])
	assert.Equal(t, "/bin/myprog", argv[3])

	useMock("no-valgrind", "valgrind")
	spec.ConfigureValgrind()
	assert.Equal(t, false, spec.shouldUseValgrind)
	assert.Equal(t, false, spec.shouldUseCallgrind)
}
