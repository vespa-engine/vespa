// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// load default environment variables (from $VESPA_HOME/conf/vespa/default-env.txt)
// Author: arnej

package vespa

import (
	"fmt"
	"os"
	"os/user"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

const ENV_CHECK = envvars.VESPA_ALREADY_SWITCHED_USER_TO

func CheckCorrectUser() {
	vespaUser := FindVespaUser()
	currentName := ""
	currentUser, err1 := user.Current()
	if err1 == nil {
		currentName = currentUser.Username
	} else {
		trace.Trace("user.Current() failed:", err1)
	}
	if currentName == vespaUser {
		// all OK
		return
	}
	trace.Trace("FindVespaUser():", vespaUser, "!=", currentName)
	wantName := ""
	wantUser, err2 := user.Lookup(vespaUser)
	if err2 == nil {
		wantName = wantUser.Username
	} else {
		trace.Trace("user.Lookup", vespaUser, "failed:", err2)
	}
	if currentName == wantName {
		// somewhat OK
		return
	}
	trace.Warning("not running as the VESPA_USER:", vespaUser)
	alreadyTried := os.Getenv(ENV_CHECK)
	if alreadyTried != "" {
		trace.Warning("already tried to switch user to", alreadyTried)
	}
	if wantName != vespaUser {
		trace.Warning("alternate correct user:", wantName)
	}
	if err1 != nil {
		trace.Warning("note: user.Current() failed:", err1)
	}
	if err2 != nil {
		trace.Warning("note: user.Lookup(", vespaUser, ") failed:", err2)
	}
	util.JustExitMsg("running as wrong user. Check your VESPA_USER setting")
}

// re-execute a vespa-wrapper action after switching to the vespa user
// (used by vespa-start-configserver and vespa-start-services)
func MaybeSwitchUser(action string) error {
	const SU_PROG = "vespa-run-as-vespa-user"
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
	if wantUser.Username != currUser.Username {
		trace.Trace("want to switch user from:", currUser.Username)
		trace.Trace("want to switch user to:", wantUser.Username)
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
