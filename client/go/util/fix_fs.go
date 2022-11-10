// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
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
		trace.Debug("lstat", name, "=>", info.Mode(), err)
	}
	return info, err
}

// ensure directory exist with correct owner/permissions
func (spec *FixSpec) FixDir(dirName string) {
	info, err := statNoSymlinks(dirName)
	if errors.Is(err, os.ErrNotExist) {
		trace.Trace("mkdir: ", dirName)
		err = os.MkdirAll(dirName, spec.DirMode)
		if err != nil {
			JustExitWith(err)
		}
		info, err = statNoSymlinks(dirName)
	}
	if err != nil {
		JustExitWith(err)
	}
	if !info.IsDir() {
		JustExitMsg(fmt.Sprintf("Not a directory: %s", dirName))
	}
	trace.Debug("chown: ", dirName, spec.UserId, spec.GroupId)
	err = os.Chown(dirName, spec.UserId, spec.GroupId)
	if err != nil {
		JustExitWith(err)
	}
	trace.Debug("chmod: ", dirName, spec.DirMode)
	err = os.Chmod(dirName, spec.DirMode)
	if err != nil {
		JustExitWith(err)
	}
	trace.Debug("directory ok:", dirName)
}

// ensure file gets correct owner/permissions if it exists
func (spec *FixSpec) FixFile(fileName string) {
	info, err := statNoSymlinks(fileName)
	if err != nil {
		if !errors.Is(err, os.ErrNotExist) {
			trace.Warning("stat error: ", err)
		}
		return
	}
	if info.IsDir() {
		JustExitMsg("Should not be a directory: " + fileName)
	}
	trace.Debug("chown: ", fileName, spec.UserId, spec.GroupId)
	err = os.Chown(fileName, spec.UserId, spec.GroupId)
	if err != nil {
		JustExitWith(err)
	}
	trace.Debug("chmod: ", fileName, spec.FileMode)
	err = os.Chmod(fileName, spec.FileMode)
	if err != nil {
		JustExitWith(err)
	}
}
