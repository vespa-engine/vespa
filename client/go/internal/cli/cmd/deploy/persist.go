// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"os"
	"path/filepath"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

const (
	cloudconfigDir              = ".cloudconfig"
	configsourceUrlUsedFileName = "deploy-configsource-url-used"
	sessionIdFileName           = "deploy-session-id"
)

func createCloudconfigDir() (string, error) {
	userHome, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	home := filepath.Join(userHome, cloudconfigDir)
	if err := os.MkdirAll(home, 0700); err != nil {
		return "", err
	}
	return home, nil
}

func configsourceUrlUsedFile() string {
	home, err := createCloudconfigDir()
	if err != nil {
		home = "/tmp"
	}
	return filepath.Join(home, configsourceUrlUsedFileName)
}

func createTenantDir(tenant string) string {
	home, err := createCloudconfigDir()
	if err != nil {
		util.JustExitWith(err)
	}
	tdir := filepath.Join(home, tenant)
	if err := os.MkdirAll(tdir, 0700); err != nil {
		util.JustExitWith(err)
	}
	return tdir
}

func writeConfigsourceUrlUsed(url string) {
	fn := configsourceUrlUsedFile()
	os.WriteFile(fn, []byte(url), 0600)
}

func getConfigsourceUrlUsed() string {
	fn := configsourceUrlUsedFile()
	bytes, err := os.ReadFile(fn)
	if err != nil {
		return ""
	}
	return string(bytes)
}

func writeSessionIdToFile(tenant, newSessionId string) {
	if newSessionId != "" {
		dir := createTenantDir(tenant)
		fn := filepath.Join(dir, sessionIdFileName)
		os.WriteFile(fn, []byte(newSessionId), 0600)
		trace.Trace("wrote", newSessionId, "to", fn)
	}
}

func getSessionIdFromFile(tenant string) string {
	dir := createTenantDir(tenant)
	fn := filepath.Join(dir, sessionIdFileName)
	bytes, err := os.ReadFile(fn)
	if err != nil {
		util.JustExitMsg("Could not read session id from file, and no session id supplied as argument. Exiting.")
	}
	trace.Trace("Session-id", string(bytes), "found from file", fn)
	return string(bytes)
}
