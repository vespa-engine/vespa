// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/defaults"
	"github.com/vespa-engine/vespa/client/go/prog"
	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	ENV_JDISC_EXPORT        = "jdisc_export_packages"
	JAVAVM_LD_PRELOAD       = "JAVAVM_LD_PRELOAD"
	PRELOAD                 = "PRELOAD"
	VESPA_CONTAINER_JVMARGS = "VESPA_CONTAINER_JVMARGS"
	VESPA_LOG_CONTROL_DIR   = "VESPA_LOG_CONTROL_DIR"
	VESPA_LOG_TARGET        = "VESPA_LOG_TARGET"

	LD_LIBRARY_PATH    = util.ENV_LD_LIBRARY_PATH
	LD_PRELOAD         = util.ENV_LD_PRELOAD
	VESPA_CONFIG_ID    = util.ENV_CONFIG_ID
	VESPA_SERVICE_NAME = util.ENV_SERVICE_NAME
	MALLOC_ARENA_MAX   = util.ENV_MALLOC_ARENA_MAX
)

func (c *containerBase) exportEnvSettings(ps *prog.Spec) {
	vespaHome := defaults.VespaHome()
	vlt := fmt.Sprintf("file:%s/logs/vespa/vespa.log", vespaHome)
	lcd := fmt.Sprintf("%s/var/db/vespa/logcontrol", vespaHome)
	dlp := fmt.Sprintf("%s/lib64", vespaHome)
	ps.Setenv(VESPA_LOG_TARGET, vlt)
	ps.Setenv(VESPA_LOG_CONTROL_DIR, lcd)
	ps.Setenv(VESPA_SERVICE_NAME, c.ServiceName())
	ps.Setenv(LD_LIBRARY_PATH, dlp)
	ps.Setenv(MALLOC_ARENA_MAX, "1")
	if preload := ps.Getenv(PRELOAD); preload != "" {
		ps.Setenv(JAVAVM_LD_PRELOAD, preload)
		ps.Setenv(LD_PRELOAD, preload)
	}
	if c.ConfigId() != "" {
		ps.Setenv(VESPA_CONFIG_ID, c.ConfigId())
	}
	util.OptionallyReduceTimerFrequency()
}
