// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/defaults"
	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
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
	if util.IsExecutableFile(p) {
		return p
	}
	p = fmt.Sprintf("%s/bin/%s", defaults.VespaHome(), PROG_NAME)
	if util.IsExecutableFile(p) {
		return p
	}
	panic(fmt.Errorf("not an executable file: %s", p))
}

func (rs *RunServer) WouldRun() bool {
	backticks := util.BackTicksForwardStderr
	out, err := backticks.Run(rs.ProgPath(), "-s", rs.ServiceName, "-p", rs.PidFile(), "-W")
	trace.Trace("output from -W:", out, "error:", err)
	return err == nil
}

func (rs *RunServer) Exec(prog string) {
	argv := []string{
		PROG_NAME,
		"-s", rs.ServiceName,
		"-p", rs.PidFile(),
		"--",
		prog,
	}
	for _, arg := range rs.Args {
		argv = append(argv, arg)
	}
	err := util.Execvp(rs.ProgPath(), argv)
	util.JustExitWith(err)
}
