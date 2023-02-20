// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"errors"
	"fmt"
	"os"
	"os/user"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

type FixSpec struct {
	UserId   int
	GroupId  int
	DirMode  os.FileMode
	FileMode os.FileMode
}

func NewFixSpec() FixSpec {
	return FixSpec{
		UserId:   -1,
		GroupId:  -1,
		DirMode:  0755,
		FileMode: 0644,
	}
}

func statNoSymlinks(path string) (info os.FileInfo, err error) {
	components := strings.Split(path, "/")
	var name string
	for idx, x := range components {
		if idx == 0 {
			name = x
			if x == "" {
				continue
			}
		} else {
			name = name + "/" + x
		}
		info, err = os.Lstat(name)
		if err != nil {
			return
		}
		if (info.Mode() & os.ModeSymlink) != 0 {
			return nil, fmt.Errorf("the path '%s' is a symlink, not allowed", name)
		}
		trace.SpamDebug("lstat", name, "=>", info.Mode(), err)
	}
	return info, err
}

// ensure directory exists with suitable permissions
func (spec *FixSpec) FixDir(dirName string) {
	info, err := statNoSymlinks(dirName)
	if errors.Is(err, os.ErrNotExist) {
		trace.Trace("mkdir: ", dirName)
		err = os.MkdirAll(dirName, spec.DirMode)
		if err != nil {
			spec.complainAndExit(err, dirName, spec.DirMode)
		}
		info, err = statNoSymlinks(dirName)
	}
	if err != nil {
		spec.complainAndExit(err, dirName, spec.DirMode)
	}
	if !info.IsDir() {
		err = fmt.Errorf("Not a directory: '%s'", dirName)
		spec.complainAndExit(err, dirName, spec.DirMode)
	}
	trace.SpamDebug("chown: ", dirName, spec.UserId, spec.GroupId)
	err = os.Chown(dirName, spec.UserId, spec.GroupId)
	if err != nil {
		spec.ensureWritableDir(dirName)
		return
	}
	trace.SpamDebug("chmod: ", dirName, spec.DirMode)
	err = os.Chmod(dirName, spec.DirMode)
	if err != nil {
		spec.ensureWritableDir(dirName)
	}
	trace.Debug("directory ok:", dirName)
}

// ensure file has suitable permissions if it exists
func (spec *FixSpec) FixFile(fileName string) {
	info, err := statNoSymlinks(fileName)
	if err != nil {
		if !errors.Is(err, os.ErrNotExist) {
			spec.complainAndExit(err, fileName, spec.FileMode)
		}
		return
	}
	if info.IsDir() {
		err = fmt.Errorf("Should not be a directory: '%s'", fileName)
		spec.complainAndExit(err, fileName, spec.FileMode)
	}
	trace.SpamDebug("chown: ", fileName, spec.UserId, spec.GroupId)
	err = os.Chown(fileName, spec.UserId, spec.GroupId)
	if err != nil {
		spec.ensureWritableFile(fileName)
		return
	}
	trace.SpamDebug("chmod: ", fileName, spec.FileMode)
	err = os.Chmod(fileName, spec.FileMode)
	if err != nil {
		spec.ensureWritableFile(fileName)
	}
}

func (spec *FixSpec) ensureWritableFile(fileName string) {
	f, err := os.OpenFile(fileName, os.O_APPEND|os.O_RDWR, spec.FileMode)
	if err == nil {
		f.Close()
		return
	}
	trace.Warning(err, "- will try to remove this file")
	err = os.Remove(fileName)
	if err != nil {
		trace.Warning("Could neither write to nor remove '" + fileName + "'")
		spec.complainAndExit(err, fileName, spec.FileMode)
	}
}

func (spec *FixSpec) ensureWritableDir(dirName string) {
	tmpFile, err := os.CreateTemp(dirName, "tmp.probe.*.tmp")
	if err != nil {
		trace.Warning("Could not create a file in directory '" + dirName + "'")
		spec.complainAndExit(err, dirName, spec.DirMode)
	}
	tmpFile.Close()
	err = os.Remove(tmpFile.Name())
	if err != nil {
		spec.complainAndExit(err, dirName, spec.DirMode)
	}
}

func (spec *FixSpec) complainAndExit(got error, fn string, wanted os.FileMode) {
	trace.Warning("problem:", got)
	currentUser, _ := user.Current()
	trace.Warning("Currently running as user:", currentUser.Username)
	trace.Warning("Wanted", fn, "to be owned by user id:", spec.UserId)
	trace.Warning("Wanted", fn, "to have group id:", spec.GroupId)
	trace.Warning("Wanted", fn, "to have permissions:", wanted)
	trace.Warning("current status of", fn, "is:")
	out, _ := BackTicksWithStderr.Run("stat", "--", fn)
	trace.Warning(out)
	trace.Warning("this is a fatal error!")
	JustExitWith(got)
}
