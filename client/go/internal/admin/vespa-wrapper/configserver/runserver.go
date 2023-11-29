// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
	"github.com/vespa-engine/vespa/client/go/internal/list"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
)

const (
	PROG_NAME = "vespa-runserver"
)

type RunServer struct {
	ServiceName string
	Args        []string
}

func (rs *RunServer) PidFile() string {
	varRunDir := defaults.UnderVespaHome("var/run")
	return fmt.Sprintf("%s/%s.pid", varRunDir, rs.ServiceName)
}

func (rs *RunServer) ProgPath() string {
	p := fmt.Sprintf("%s/bin64/%s", defaults.VespaHome(), PROG_NAME)
	if ioutil.IsExecutable(p) {
		return p
	}
	p = fmt.Sprintf("%s/bin/%s", defaults.VespaHome(), PROG_NAME)
	if ioutil.IsExecutable(p) {
		return p
	}
	panic(fmt.Errorf("not an executable file: %s", p))
}

func (rs *RunServer) WouldRun() bool {
	backticks := osutil.BackTicksForwardStderr
	out, err := backticks.Run(rs.ProgPath(), "-s", rs.ServiceName, "-p", rs.PidFile(), "-W")
	trace.Trace("output from -W:", out, "error:", err)
	return err == nil
}

func (rs *RunServer) Exec(prog string) {
	argv := list.ArrayList[string]{
		PROG_NAME,
		"-s", rs.ServiceName,
		"-r", "30",
		"-p", rs.PidFile(),
		"--",
		prog,
	}
	argv.AppendAll(rs.Args...)
	err := osutil.Execvp(rs.ProgPath(), argv)
	osutil.ExitErr(err)
}
