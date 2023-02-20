// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// load default environment variables (from $VESPA_HOME/conf/vespa/default-env.txt)
// Author: arnej

package vespa

import (
	"os"
	"os/user"
	"strconv"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
)

// Which user should vespa services run as?  If current user is root,
// we want to change to some non-privileged user.
// Should be run after LoadDefaultEnv() which possibly loads VESPA_USER
// Which user should vespa services run as?  If current user is root,
// we want to change to some non-privileged user.
// Should be run after LoadDefaultEnv() which possibly loads VESPA_USER
func FindVespaUser() string {
	uName := os.Getenv(envvars.VESPA_USER)
	if uName != "" {
		// no check here, assume valid
		return uName
	}
	if os.Getuid() == 0 {
		u, err := user.Lookup("vespa")
		if err == nil {
			uName = u.Username
		} else {
			u, err = user.Lookup("nobody")
			if err == nil {
				uName = u.Username
			}
		}
	}
	if uName == "" {
		u, err := user.Current()
		if err == nil {
			uName = u.Username
		}
	}
	if uName != "" {
		os.Setenv(envvars.VESPA_USER, uName)
	}
	return uName
}

// Which user/group should vespa services run as?  If current user is root,
// we want to change to some non-privileged user.
// Should be run after LoadDefaultEnv() which possibly loads VESPA_USER

func FindVespaUidAndGid() (userId, groupId int) {
	userId = -1
	groupId = -1
	uName := os.Getenv(envvars.VESPA_USER)
	gName := os.Getenv(envvars.VESPA_GROUP)
	if uName == "" {
		uName = FindVespaUser()
	}
	if uName != "" {
		u, err := user.Lookup(uName)
		if err == nil {
			userId, _ = strconv.Atoi(u.Uid)
			if gName == "" {
				groupId, _ = strconv.Atoi(u.Gid)
			}
		}
	}
	if gName != "" {
		g, err := user.LookupGroup(gName)
		if err == nil {
			groupId, _ = strconv.Atoi(g.Gid)
		}
	}
	return
}
