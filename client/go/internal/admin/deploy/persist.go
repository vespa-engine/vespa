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
	dir := filepath.Join(os.TempDir(), cloudconfigDir)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return "", err
	}
	return dir, nil
}

func configsourceUrlUsedFile() string {
	dir, err := createCloudconfigDir()
	if err != nil {
		dir = "/tmp"
	}
	return filepath.Join(dir, configsourceUrlUsedFileName)
}

func createTenantDir(tenant string) string {
	dir, err := createCloudconfigDir()
	if err != nil {
		util.JustExitWith(err)
	}
	tdir := filepath.Join(dir, tenant)
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
