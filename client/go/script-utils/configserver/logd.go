// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

func maybeStartLogd() {
	v1 := os.Getenv("cloudconfig_server__multitenant")
	v2 := os.Getenv("VESPA_CONFIGSERVER_MULTITENANT")
	if v1 == "true" || v2 == "true" {
		backticks := util.BackTicksForwardStderr
		out, err := backticks.Run("libexec/vespa/start-logd")
		if err != nil {
			panic(err)
		}
		trace.Info(out)
	}
}
