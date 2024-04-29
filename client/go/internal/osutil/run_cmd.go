// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package osutil

import (
	"bytes"
	"fmt"
	"os"
	"os/exec"
	"strings"
	"syscall"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

type BackTicks int

const (
	BackTicksWithStderr BackTicks = iota
	BackTicksIgnoreStderr
	BackTicksForwardStderr
	SystemCommand
)

func analyzeError(err error) string {
	exitErr, wasEe := err.(*exec.ExitError)
	if !wasEe {
		return ""
	}
	status, wasWs := exitErr.ProcessState.Sys().(syscall.WaitStatus)
	if !wasWs {
		return err.Error()
	}
	if !status.Signaled() {
		return ""
	}
	msg := "died with signal: " + status.Signal().String()
	switch status.Signal() {
	case syscall.SIGILL:
		msg = msg + " (you probably have an older CPU than required, see https://docs.vespa.ai/en/cpu-support.html)"
	}
	return msg
}

func (b BackTicks) Run(program string, args ...string) (string, error) {
	cmd := exec.Command(program, args...)
	var out bytes.Buffer
	cmd.Stdout = &out
	switch b {
	case BackTicksWithStderr:
		cmd.Stderr = &out
	case BackTicksIgnoreStderr:
		cmd.Stderr = nil
	case BackTicksForwardStderr:
		cmd.Stderr = os.Stderr
	case SystemCommand:
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
	}
	trace.Debug("running command:", program, strings.Join(args, " "))
	err := cmd.Run()
	if extraMsg := analyzeError(err); extraMsg != "" {
		fmt.Fprintln(os.Stderr, "Problem running program", program, "=>", extraMsg)
	}
	return out.String(), err
}
