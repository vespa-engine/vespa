// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func (opts *Options) exportEnvSettings(ps *prog.Spec) {
	c := opts.container
	vespaHome := defaults.VespaHome()
	lvd := fmt.Sprintf("%s/logs/vespa", vespaHome)
	vlt := fmt.Sprintf("file:%s/vespa.log", lvd)
	lcd := fmt.Sprintf("%s/var/db/vespa/logcontrol", vespaHome)
	lcf := fmt.Sprintf("%s/%s.logcontrol", lcd, c.ServiceName())
	dlp := fmt.Sprintf("%s/lib64:/opt/vespa-deps/lib64", vespaHome)
	opts.fixSpec.FixDir(lvd)
	opts.fixSpec.FixDir(lcd)
	ps.Setenv(envvars.VESPA_LOG_TARGET, vlt)
	ps.Setenv(envvars.VESPA_LOG_CONTROL_DIR, lcd)
	ps.Setenv(envvars.VESPA_LOG_CONTROL_FILE, lcf)
	ps.Setenv(envvars.VESPA_SERVICE_NAME, c.ServiceName())
	ps.Setenv(envvars.LD_LIBRARY_PATH, dlp)
	ps.Setenv(envvars.MALLOC_ARENA_MAX, "1")
	if preload := ps.Getenv(envvars.PRELOAD); preload != "" {
		ps.Setenv(envvars.JAVAVM_LD_PRELOAD, preload)
		ps.Setenv(envvars.LD_PRELOAD, preload)
	}
	util.OptionallyReduceTimerFrequency()
	c.exportExtraEnv(ps)
}
