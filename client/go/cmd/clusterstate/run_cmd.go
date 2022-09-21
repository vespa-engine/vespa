// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"bytes"
	"os"
	"os/exec"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
)

func getOutputFromCmd(program string, args ...string) (string, error) {
	cmd := exec.Command(program, args...)
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = os.Stderr
	trace.Debug("running command:", program, strings.Join(args, " "))
	err := cmd.Run()
	return out.String(), err
}
