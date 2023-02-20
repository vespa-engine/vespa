// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package vespa

import (
	"io/fs"
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func RunPreStart() error {
	vespaHome := FindAndVerifyVespaHome()
	err := LoadDefaultEnv()
	if err != nil {
		return err
	}
	trace.Trace("chdir:", vespaHome)
	err = os.Chdir(vespaHome)
	if err != nil {
		return err
	}
	vespaUid, vespaGid := FindVespaUidAndGid()
	fixSpec := util.FixSpec{
		UserId:   vespaUid,
		GroupId:  vespaGid,
		DirMode:  0755,
		FileMode: 0644,
	}
	fixSpec.FixDir("logs")
	fixSpec.FixDir("logs/vespa")
	fixSpec.FixDir("logs/vespa/configserver")
	fixSpec.FixDir("logs/vespa/search")
	fixSpec.FixDir("var/tmp")
	fixSpec.FixDir("var/tmp/vespa")
	fixSpec.FixDir("var")
	fixSpec.FixDir("var/crash")
	fixSpec.FixDir("var/db/vespa")
	fixSpec.FixDir("var/db/vespa/config_server")
	fixSpec.FixDir("var/db/vespa/config_server/serverdb")
	fixSpec.FixDir("var/db/vespa/config_server/serverdb/tenants")
	fixSpec.FixDir("var/db/vespa/filedistribution")
	fixSpec.FixDir("var/db/vespa/index")
	fixSpec.FixDir("var/db/vespa/logcontrol")
	fixSpec.FixDir("var/db/vespa/search")
	fixSpec.FixDir("var/db/vespa/tmp")
	fixSpec.FixDir("var/jdisc_container")
	fixSpec.FixDir("var/run")
	fixSpec.FixDir("var/vespa")
	fixSpec.FixDir("var/vespa/application")
	fixSpec.FixDir("var/vespa/bundlecache")
	fixSpec.FixDir("var/vespa/bundlecache/configserver")
	fixSpec.FixDir("var/vespa/cache")
	fixSpec.FixDir("var/vespa/cache/config")
	// fix wrong ownerships within directories:
	var fixer fs.WalkDirFunc = func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			util.JustExitWith(err)
		}
		if d.IsDir() {
			fixSpec.FixDir(path)
		} else if d.Type().IsRegular() {
			fixSpec.FixFile(path)
		}
		return nil
	}
	fileSystem := os.DirFS(vespaHome)
	fs.WalkDir(fileSystem, "logs/vespa", fixer)
	fs.WalkDir(fileSystem, "var/db/vespa", fixer)
	// we used to have (Vespa 7) a directory "qrs", with "access" a symlink to that.
	// now we want it the other way around, try to make it so:
	const lva string = "logs/vespa/access"
	const lvq string = "logs/vespa/qrs"
	info, err := os.Lstat(lva)
	if err == nil && (info.Mode()&os.ModeSymlink) != 0 {
		err = os.Remove(lva)
		if err != nil {
			return err
		}
	}
	fixSpec.FixDir(lva)
	// best-effort fixup, not trying too hard to get backwards compatibility:
	_ = os.Remove(lvq)
	_ = os.Symlink("access", lvq)
	return nil
}
