// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func makeFixSpec() osutil.FixSpec {
	vespaUid, vespaGid := vespa.FindVespaUidAndGid()
	return osutil.FixSpec{
		UserId:   vespaUid,
		GroupId:  vespaGid,
		DirMode:  0755,
		FileMode: 0644,
	}
}

func fixDirsAndFiles(fixSpec osutil.FixSpec) {
	fixSpec.FixDir("var/zookeeper")
	fixSpec.FixDir("var/zookeeper/conf")
	fixSpec.FixDir("var/zookeeper/version-2")
	fixSpec.FixFile("var/zookeeper/conf/zookeeper.cfg")
	fixSpec.FixFile("var/zookeeper/myid")
}
