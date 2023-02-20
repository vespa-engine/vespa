// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

const (
	ZOOKEEPER_LOG_FILE_PREFIX = "logs/vespa/zookeeper"
)

func RemoveStaleZkLocks(c Container) {
	backticks := util.BackTicksWithStderr
	cmd := fmt.Sprintf("rm -f '%s/%s.%s'*lck", defaults.VespaHome(), ZOOKEEPER_LOG_FILE_PREFIX, c.ServiceName())
	trace.Trace("cleaning locks:", cmd)
	out, err := backticks.Run("/bin/sh", "-c", cmd)
	if err != nil {
		trace.Warning("Failure [", out, "] when running command:", cmd)
		util.JustExitWith(err)
	}
}
