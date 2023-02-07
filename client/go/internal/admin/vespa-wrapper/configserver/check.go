// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func checkIsConfigserver(myname string) {
	onlyHosts := defaults.VespaConfigserverHosts()
	for _, hn := range onlyHosts {
		if hn == "localhost" || hn == myname {
			trace.Debug("should run configserver:", hn)
			return
		}
	}
	trace.Warning("only these hosts should run a config server:", onlyHosts)
	util.JustExitMsg(fmt.Sprintf("this host [%s] should not run a config server", myname))
}
