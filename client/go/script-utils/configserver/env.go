// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/util"
)

func exportSettings(vespaHome string) {
	vlt := fmt.Sprintf("file:%s/logs/vespa/vespa.log", vespaHome)
	lcd := fmt.Sprintf("%s/var/db/vespa/logcontrol", vespaHome)
	lcf := fmt.Sprintf("%s/configserver.logcontrol", lcd)
	dlp := fmt.Sprintf("%s/lib64", vespaHome)
	app := fmt.Sprintf("%s/conf/configserver-app", vespaHome)
	os.Setenv("VESPA_LOG_TARGET", vlt)
	os.Setenv("VESPA_LOG_CONTROL_DIR", lcd)
	os.Setenv("VESPA_LOG_CONTROL_FILE", lcf)
	os.Setenv("VESPA_SERVICE_NAME", "configserver")
	os.Setenv("LD_LIBRARY_PATH", dlp)
	os.Setenv("JAVAVM_LD_PRELOAD", "")
	os.Setenv("LD_PRELOAD", "")
	os.Setenv("standalone_jdisc_container__app_location", app)
	os.Setenv("standalone_jdisc_container__deployment_profile", "configserver")
	os.Setenv("MALLOC_ARENA_MAX", "1")
	util.OptionallyReduceTimerFrequency()
}
