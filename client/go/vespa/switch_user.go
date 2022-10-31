// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// load default environment variables (from $VESPA_HOME/conf/vespa/default-env.txt)
// Author: arnej

package vespa

import (
	"fmt"
	"os"
	"os/user"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

// re-execute a script-utils action after switching to the vespa user
// (used by vespa-start-configserver and vespa-start-services)
func MaybeSwitchUser(action string) error {
	const SU_PROG = "vespa-run-as-vespa-user"
	const ENV_CHECK = "VESPA_ALREADY_SWITCHED_USER_TO"
	vespaHome := FindHome()
	vespaUser := FindVespaUser()

	wantUser, err := user.Lookup(vespaUser)
	if err != nil {
		trace.Trace("user.Lookup", vespaUser, "failed:", err)
		return err
	}
	currUser, err := user.Current()
	if err != nil {
		trace.Trace("user.Current() failed:", err)
		return err
	}
	trace.Trace("want to switch user from:", currUser.Username)
	trace.Trace("want to switch user to:", wantUser.Username)
	if wantUser.Username != currUser.Username {
		alreadyTried := os.Getenv(ENV_CHECK)
		if alreadyTried != "" {
			// safety check to avoid infinite loop
			trace.Warning("already tried to switch user to", alreadyTried)
			return fmt.Errorf("could not switch user to %s", wantUser.Username)
		}
		mySelf := fmt.Sprintf("%s/%s", vespaHome, scriptUtilsFilename)
		os.Setenv(ENV_CHECK, wantUser.Username)
		args := []string{SU_PROG, mySelf, action}
		return util.Execvp(SU_PROG, args)
	}
	return nil
}
