// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"bytes"
	"os"
	"os/exec"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
)

// this is basically shell backticks:
func GetOutputFromProgram(program string, args ...string) (string, error) {
	cmd := exec.Command(program, args...)
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = os.Stderr
	trace.Debug("running command:", program, strings.Join(args, " "))
	err := cmd.Run()
	return out.String(), err
}
