// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package util

import (
	"os"
	"os/user"
	"path/filepath"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func setup(t *testing.T) string {
	tt := t.TempDir()
	tmpDir, _ := filepath.EvalSymlinks(tt)
	err := os.MkdirAll(tmpDir+"/a", 0755)
	assert.Nil(t, err)
	err = os.MkdirAll(tmpDir+"/a/bad", 0)
	assert.Nil(t, err)
	err = os.WriteFile(tmpDir+"/a/f1", []byte{10}, 0644)
	assert.Nil(t, err)
	err = os.WriteFile(tmpDir+"/a/f2", []byte{10}, 0111)
	return tmpDir
}

func testFixSpec(t *testing.T, spec FixSpec) {
	tmpDir := setup(t)
	spec.FixDir(tmpDir + "/a")
	spec.FixDir(tmpDir + "/b")
	spec.FixDir(tmpDir + "/a/bad")
	spec.FixDir(tmpDir + "/a/bad/ok")
	spec.FixFile(tmpDir + "/a/f1")
	spec.FixFile(tmpDir + "/a/f2")
	spec.FixFile(tmpDir + "/a/f3")
	spec.FixFile(tmpDir + "/b/f4")
	spec.FixFile(tmpDir + "/a/bad/f5")
	assert.Equal(t, true, IsDirectory(tmpDir+"/a"))
	assert.Equal(t, true, IsDirectory(tmpDir+"/b"))
	assert.Equal(t, true, IsDirectory(tmpDir+"/a/bad"))
	assert.Equal(t, true, IsDirectory(tmpDir+"/a/bad/ok"))
	assert.Equal(t, true, IsRegularFile(tmpDir+"/a/f1"))
	assert.Equal(t, true, IsRegularFile(tmpDir+"/a/f2"))
	assert.Equal(t, false, IsRegularFile(tmpDir+"/a/f3"))
	assert.Equal(t, false, IsRegularFile(tmpDir+"/b/f4"))
	assert.Equal(t, false, IsRegularFile(tmpDir+"/a/bad/f5"))

	info, err := os.Stat(tmpDir + "/a")
	assert.Nil(t, err)
	assert.Equal(t, true, info.IsDir())
	assert.Equal(t, 0755, int(info.Mode())&0777)

	info, err = os.Stat(tmpDir + "/b")
	assert.Nil(t, err)
	assert.Equal(t, true, info.IsDir())
	assert.Equal(t, 0755, int(info.Mode())&0777)

	info, err = os.Stat(tmpDir + "/a/bad")
	assert.Nil(t, err)
	assert.Equal(t, true, info.IsDir())
	assert.Equal(t, 0755, int(info.Mode())&0777)

	info, err = os.Stat(tmpDir + "/a/bad/ok")
	assert.Nil(t, err)
	assert.Equal(t, true, info.IsDir())
	assert.Equal(t, 0755, int(info.Mode())&0777)

	info, err = os.Stat(tmpDir + "/a/f1")
	assert.Nil(t, err)
	assert.Equal(t, false, info.IsDir())
	assert.Equal(t, 0644, int(info.Mode())&0777)

	info, err = os.Stat(tmpDir + "/a/f2")
	assert.Nil(t, err)
	assert.Equal(t, false, info.IsDir())
	assert.Equal(t, 0644, int(info.Mode())&0777)
}

func TestSimpleFixes(t *testing.T) {
	testFixSpec(t, NewFixSpec())
}

func TestSuperUserOnly(t *testing.T) {
	trace.AdjustVerbosity(0)
	var userId int = -1
	var groupId int = -1
	if os.Getuid() != 0 {
		trace.Trace("skip TestSuperUserOnly, uid != 0")
		return
	}
	u, err := user.Current()
	if u.Username != "root" {
		trace.Trace("skip TestSuperUserOnly, user != root")
		return
	}
	u, err = user.Lookup("nobody")
	if err != nil {
		trace.Trace("skip TestSuperUserOnly, user nobody was not found")
		return
	}
	userId, err = strconv.Atoi(u.Uid)
	if err != nil || userId < 1 {
		trace.Trace("skip TestSuperUserOnly, user ID of nobody was not found")
		return
	}
	g, err := user.LookupGroup("users")
	if err == nil {
		groupId, _ = strconv.Atoi(g.Gid)
	}
	fixSpec := NewFixSpec()
	fixSpec.UserId = userId
	if groupId > 0 {
		fixSpec.GroupId = groupId
	}
	testFixSpec(t, fixSpec)
}

func expectSimplePanic() {
	if r := recover(); r != nil {
		if jee, ok := r.(*JustExitError); ok {
			trace.Trace("got as expected:", jee)
			return
		}
		panic(r)
	}
}

func TestFailedFixdir(t *testing.T) {
	tmpDir := setup(t)
	spec := NewFixSpec()
	defer expectSimplePanic()
	spec.FixDir(tmpDir + "/a/f1")
	assert.Equal(t, "", "should not be reached")
}

func TestFailedFixfile(t *testing.T) {
	tmpDir := setup(t)
	spec := NewFixSpec()
	defer expectSimplePanic()
	spec.FixFile(tmpDir + "/a")
	assert.Equal(t, "", "should not be reached")
}
