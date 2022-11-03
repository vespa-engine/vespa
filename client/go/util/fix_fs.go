// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"errors"
	"fmt"
	"os"

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

// ensure directory exist with correct owner/permissions
func (spec *FixSpec) FixDir(dirName string) {
	info, err := os.Stat(dirName)
	if err != nil {
		trace.Trace("mkdir: ", dirName)
		err = os.MkdirAll(dirName, spec.DirMode)
		if err != nil {
			JustExitWith(err)
		}
		info, err = os.Stat(dirName)
		if err != nil {
			JustExitWith(err)
		}
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
	trace.Trace("directory ok:", dirName)
}

// ensure file gets correct owner/permissions if it exists
func (spec *FixSpec) FixFile(fileName string) {
	info, err := os.Stat(fileName)
	if err != nil {
		if !errors.Is(err, os.ErrNotExist) {
			trace.Warning("stat error: ", err, "containing:", err.(*os.PathError).Unwrap())
		}
		return
	}
	if info.IsDir() {
		panic(fmt.Errorf("Should not be a directory: %s", fileName))
	}
	trace.Debug("chown: ", fileName, spec.UserId, spec.GroupId)
	err = os.Chown(fileName, spec.UserId, spec.GroupId)
	if err != nil {
		panic(err)
	}
	trace.Debug("chmod: ", fileName, spec.FileMode)
	err = os.Chmod(fileName, spec.FileMode)
	if err != nil {
		panic(err)
	}
}
