// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package main

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/vespa"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "actions: export-env, ipv6-only")
		return
	}
	switch os.Args[1] {
	case "export-env":
		vespa.ExportDefaultEnvToSh()
	case "ipv6-only":
		if vespa.HasOnlyIpV6() {
			os.Exit(0)
		} else {
			os.Exit(1)
		}
	default:
		fmt.Fprintf(os.Stderr, "unknown action '%s'\n", os.Args[1])
	}
}
