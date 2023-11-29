// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
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
		checked := []string{}
		for _, fileName := range strings.Split(preload, ":") {
			if ioutil.Exists(fileName) {
				checked = append(checked, fileName)
			} else {
				trace.Info("File in PRELOAD missing, skipped:", fileName)
			}
		}
		if len(checked) > 0 {
			preload := strings.Join(checked, ":")
			ps.Setenv(envvars.JAVAVM_LD_PRELOAD, preload)
			ps.Setenv(envvars.LD_PRELOAD, preload)
		}
	}
	osutil.OptionallyReduceTimerFrequency()
	c.exportExtraEnv(ps)
}
