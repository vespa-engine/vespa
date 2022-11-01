// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	ZOOKEEPER_LOG_FILE_PREFIX = "logs/vespa/zookeeper.configserver"
)

func removeStaleZkLocks(vespaHome string) {
	backticks := util.BackTicksIgnoreStderr
	cmd := fmt.Sprintf("rm -f '%s/%s'*lck", vespaHome, ZOOKEEPER_LOG_FILE_PREFIX)
	trace.Trace("cleaning locks:", cmd)
	backticks.Run("/bin/sh", "-c", cmd)
}
