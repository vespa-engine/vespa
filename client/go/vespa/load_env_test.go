// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestLoadEnv(t *testing.T) {
	// setup
	tmp := os.TempDir() + "/load_env_test.tmp"
	vdir := tmp + "/vespa"
	cdir := vdir + "/conf/vespa"
	envf := cdir + "/default-env.txt"
	err := os.MkdirAll(cdir, 0755)
	defer os.RemoveAll(tmp)
	assert.Nil(t, err)
	fmt.Println("vdir:", vdir)
	os.Setenv("VESPA_HOME", vdir)
	os.Setenv("VESPA_FOO", "was foo")
	os.Setenv("VESPA_BAR", "was bar")
	os.Setenv("VESPA_FOOBAR", "foobar")
	os.Unsetenv("VESPA_QUUX")
	contents := `# vespa env vars file
override VESPA_FOO "new foo"
fallback VESPA_BAR "new bar"
fallback VESPA_QUUX "new quux"
unset VESPA_FOOBAR
override VESPA_V1 v1
 override  VESPA_V2  v2 
override VESPA_V3 spaced v3 v3
override VESPA_V4 " quoted spaced "

some junk here
override VESPA_V5 v5
`
	err = os.WriteFile(envf, []byte(contents), 0644)
	assert.Nil(t, err)
	// run it
	err = LoadDefaultEnv()
	// check results
	assert.Equal(t, os.Getenv("VESPA_FOO"), "new foo")
	assert.Equal(t, os.Getenv("VESPA_BAR"), "was bar")
	assert.Equal(t, os.Getenv("VESPA_FOOBAR"), "")
	assert.Equal(t, os.Getenv("VESPA_QUUX"), "new quux")
	assert.Equal(t, os.Getenv("VESPA_V1"), "v1")
	assert.Equal(t, os.Getenv("VESPA_V2"), "v2")
	assert.Equal(t, os.Getenv("VESPA_V3"), "spaced v3 v3")
	assert.Equal(t, os.Getenv("VESPA_V4"), " quoted spaced ")
	assert.Equal(t, os.Getenv("VESPA_V5"), "v5")
	_, present := os.LookupEnv("VESPA_FOOBAR")
	assert.Equal(t, present, false)
	assert.NotNil(t, err)
	assert.Equal(t, err.Error(), "unknown action 'some'")
}
