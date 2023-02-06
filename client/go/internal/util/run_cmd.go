// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"bytes"
	"os"
	"os/exec"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

type BackTicks int

const (
	BackTicksWithStderr BackTicks = iota
	BackTicksIgnoreStderr
	BackTicksForwardStderr
	SystemCommand
)

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
	return out.String(), err
}
