// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
)

func setup(t *testing.T, contents string) {
	tmp := t.TempDir() + "/load_env_test.tmp"
	vdir := tmp + "/vespa"
	cdir := vdir + "/conf/vespa"
	envf := cdir + "/default-env.txt"
	err := os.MkdirAll(cdir, 0755)
	assert.Nil(t, err)
	os.Setenv("VESPA_HOME", vdir)
	err = os.WriteFile(envf, []byte(contents), 0644)
	assert.Nil(t, err)
}

func TestLoadEnvSimple(t *testing.T) {
	os.Setenv("VESPA_FOO", "was foo")
	os.Setenv("VESPA_BAR", "was bar")
	os.Setenv("VESPA_FOOBAR", "foobar")
	os.Unsetenv("VESPA_QUUX")
	setup(t, `
# vespa env vars file
override VESPA_FOO "new foo"

fallback VESPA_BAR "new bar"
fallback VESPA_QUUX "new quux"

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
	os.Setenv("VESPA_FOO", "was foo")
	os.Setenv("VESPA_BAR", "was bar")
	os.Setenv("VESPA_FOOBAR", "foobar")
	os.Setenv("VESPA_BARFOO", "was barfoo")
	os.Unsetenv("VESPA_QUUX")
	setup(t, `
# vespa env vars file
override VESPA_FOO "newFoo1"

fallback VESPA_BAR "new bar"
fallback VESPA_QUUX "new quux"

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
	// unsets:
	assert.Equal(t, "", holder.exportVars["VESPA_FOOBAR"])
	assert.Equal(t, "unset", holder.unsetVars["VESPA_FOOBAR"])
	assert.Equal(t, "", holder.exportVars["XYZ"])
	assert.Equal(t, "unset", holder.unsetVars["XYZ"])
	// nothing extra allowed:
	assert.Equal(t, 3, len(holder.exportVars))
	assert.Equal(t, 2, len(holder.unsetVars))
	// run it
	err = ExportDefaultEnvToSh()
	assert.Nil(t, err)
}
