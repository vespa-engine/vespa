// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func exportSettings(vespaHome string) {
	vlt := fmt.Sprintf("file:%s/logs/vespa/vespa.log", vespaHome)
	lcd := fmt.Sprintf("%s/var/db/vespa/logcontrol", vespaHome)
	lcf := fmt.Sprintf("%s/configserver.logcontrol", lcd)
	dlp := fmt.Sprintf("%s/lib64", vespaHome)
	app := fmt.Sprintf("%s/conf/configserver-app", vespaHome)
	os.Setenv(envvars.VESPA_LOG_TARGET, vlt)
	os.Setenv(envvars.VESPA_LOG_CONTROL_DIR, lcd)
	os.Setenv(envvars.VESPA_LOG_CONTROL_FILE, lcf)
	os.Setenv(envvars.VESPA_SERVICE_NAME, "configserver")
	os.Setenv(envvars.LD_LIBRARY_PATH, dlp)
	os.Setenv(envvars.JAVAVM_LD_PRELOAD, "")
	os.Setenv(envvars.LD_PRELOAD, "")
	os.Setenv(envvars.STANDALONE_JDISC_APP_LOCATION, app)
	os.Setenv(envvars.STANDALONE_JDISC_DEPLOYMENT_PROFILE, "configserver")
	os.Setenv(envvars.MALLOC_ARENA_MAX, "1")
	util.OptionallyReduceTimerFrequency()
}
