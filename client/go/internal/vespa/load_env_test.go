// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"fmt"
	"os"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func setup(t *testing.T, contents string) string {
	td := t.TempDir()
	tmp := td + "/load_env_test.tmp"
	vdir := tmp + "/vespa"
	bdir := vdir + "/bin"
	cdir := vdir + "/conf/vespa"
	envf := cdir + "/default-env.txt"
	err := os.MkdirAll(cdir, 0755)
	assert.Nil(t, err)
	t.Setenv("VESPA_HOME", vdir)
	err = os.MkdirAll(bdir, 0755)
	assert.Nil(t, err)
	err = os.WriteFile(envf, []byte(contents), 0644)
	assert.Nil(t, err)
	return tmp
}

func TestLoadEnvSimple(t *testing.T) {
	trace.AdjustVerbosity(0)
	t.Setenv("VESPA_FOO", "was foo")
	t.Setenv("VESPA_BAR", "was bar")
	t.Setenv("VESPA_FOOBAR", "foobar")
	os.Unsetenv("VESPA_QUUX")
	setup(t, `
# vespa env vars file
override VESPA_FOO "new foo"

fallback VESPA_BAR "new bar"
fallback VESPA_QUUX "new quux"
fallback VESPA_QUUX "bad quux"

unset VESPA_FOOBAR
`)
	// run it
	err := LoadDefaultEnv()
	assert.Nil(t, err)
	// check results
	assert.Equal(t, os.Getenv("VESPA_FOO"), "new foo")
	assert.Equal(t, os.Getenv("VESPA_BAR"), "was bar")
	assert.Equal(t, os.Getenv("VESPA_FOOBAR"), "")
	assert.Equal(t, os.Getenv("VESPA_QUUX"), "new quux")
	_, present := os.LookupEnv("VESPA_FOOBAR")
	assert.Equal(t, present, false)
}

func TestLoadEnvWhiteSpace(t *testing.T) {
	// note trailing whitespace below!
	setup(t, `
# vespa env vars file
override VESPA_V1 v1
 override  VESPA_V2  v2 
override VESPA_V3 spaced v3 v3
override VESPA_V4 " quoted spaced "
override VESPA_V5 v5
`)
	// run it
	err := LoadDefaultEnv()
	assert.Nil(t, err)
	// check results
	assert.Equal(t, os.Getenv("VESPA_V1"), "v1")
	assert.Equal(t, os.Getenv("VESPA_V2"), "v2")
	assert.Equal(t, os.Getenv("VESPA_V3"), "spaced v3 v3")
	assert.Equal(t, os.Getenv("VESPA_V4"), " quoted spaced ")
	assert.Equal(t, os.Getenv("VESPA_V5"), "v5")
}

func TestLoadEnvBadAction(t *testing.T) {
	setup(t, `
# vespa env vars file
override VESPA_V1 v1
some junk here
override VESPA_V2 v2
`)
	// run it
	err := LoadDefaultEnv()
	// check results
	assert.Equal(t, os.Getenv("VESPA_V1"), "v1")
	assert.Equal(t, os.Getenv("VESPA_V2"), "v2")
	assert.NotNil(t, err)
	assert.Equal(t, err.Error(), "unknown action 'some'")
}

func TestLoadEnvBadVar(t *testing.T) {
	setup(t, `
# vespa env vars file
override VESPA_V1 v1
override .A foobar
override VESPA_V2 v2
`)
	// run it
	err := LoadDefaultEnv()
	// check results
	assert.Equal(t, os.Getenv("VESPA_V1"), "v1")
	assert.Equal(t, os.Getenv("VESPA_V2"), "v2")
	assert.NotNil(t, err)
	assert.Equal(t, err.Error(), "Not a valid environment variable name: '.A'")
}

func TestFindUser(t *testing.T) {
	u := FindVespaUser()
	if u == "" {
		fmt.Fprintln(os.Stderr, "WARNING: empty result from FindVespaUser()")
	} else {
		fmt.Fprintln(os.Stderr, "INFO: result from FindVespaUser() is", u)
		assert.Equal(t, u, os.Getenv("VESPA_USER"))
	}
	setup(t, `
override VESPA_USER unprivuser
`)
	LoadDefaultEnv()
	u = FindVespaUser()
	assert.Equal(t, "unprivuser", u)
}

func TestExportEnv(t *testing.T) {
	t.Setenv("VESPA_FOO", "was foo")
	t.Setenv("VESPA_BAR", "was bar")
	t.Setenv("VESPA_FOOBAR", "foobar")
	t.Setenv("VESPA_ALREADY", "already")
	t.Setenv("VESPA_BARFOO", "was barfoo")
	os.Unsetenv("VESPA_QUUX")
	setup(t, `
# vespa env vars file
override VESPA_FOO "newFoo1"

fallback VESPA_FOO "bad foo"
fallback VESPA_BAR "new bar"
fallback VESPA_QUUX "new quux"
fallback VESPA_QUUX "bad quux"
fallback VESPA_ALREADY "already"

unset VESPA_FOOBAR
unset VESPA_BARFOO
fallback VESPA_BARFOO new'b<a>r'foo
override XYZ xyz
unset XYZ
`)
	holder := newShellEnvExporter()
	err := loadDefaultEnvTo(holder)
	assert.Nil(t, err)
	// new values:
	assert.Equal(t, "newFoo1", holder.exportVars["VESPA_FOO"])
	assert.Equal(t, "", holder.exportVars["VESPA_BAR"])
	assert.Equal(t, "'new quux'", holder.exportVars["VESPA_QUUX"])
	assert.Equal(t, `'new'\''b<a>r'\''foo'`, holder.exportVars["VESPA_BARFOO"])
	assert.Equal(t, "already", holder.exportVars["VESPA_ALREADY"])
	// unsets:
	assert.Equal(t, "", holder.exportVars["VESPA_FOOBAR"])
	assert.Equal(t, "unset", holder.unsetVars["VESPA_FOOBAR"])
	assert.Equal(t, "", holder.exportVars["XYZ"])
	assert.Equal(t, "unset", holder.unsetVars["XYZ"])
	// nothing extra allowed:
	assert.Equal(t, 4, len(holder.exportVars))
	assert.Equal(t, 2, len(holder.unsetVars))
	// run it
	err = ExportDefaultEnvToSh()
	assert.Nil(t, err)
}

func TestLoadEnvNop(t *testing.T) {
	td := setup(t, "")
	t.Setenv("PATH", td)
	err := LoadDefaultEnv()
	assert.Nil(t, err)
	// check results
	path := os.Getenv("PATH")
	fmt.Println("got path:", path)
	assert.True(t, strings.Contains(path, td+"/vespa/bin:"))
	assert.True(t, strings.Contains(path, ":"+td))
}
