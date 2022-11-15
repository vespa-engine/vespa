// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/cmd/clusterstate"
	"github.com/vespa-engine/vespa/client/go/cmd/deploy"
	"github.com/vespa-engine/vespa/client/go/cmd/logfmt"
	"github.com/vespa-engine/vespa/client/go/jvm"
	"github.com/vespa-engine/vespa/client/go/script-utils/configserver"
	"github.com/vespa-engine/vespa/client/go/script-utils/startcbinary"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func basename(s string) string {
	parts := strings.Split(s, "/")
	return parts[len(parts)-1]
}

func main() {
	defer handleSimplePanic()
	_ = vespa.FindAndVerifyVespaHome()
	action := basename(os.Args[0])
	if action == "script-utils" && len(os.Args) > 1 {
		action = os.Args[1]
		os.Args = os.Args[1:]
	}
	switch action {
	case "vespa-start-configserver":
		os.Exit(configserver.StartConfigserverEtc())
	case "just-start-configserver":
		os.Exit(configserver.JustStartConfigserver())
	case "vespa-start-container-daemon":
		os.Exit(jvm.RunApplicationContainer(os.Args[1:]))
	case "start-c-binary":
		os.Exit(startcbinary.Run(os.Args[1:]))
	case "export-env":
		vespa.ExportDefaultEnvToSh()
	case "security-env", "vespa-security-env":
		vespa.ExportSecurityEnvToSh()
	case "ipv6-only":
		if vespa.HasOnlyIpV6() {
			os.Exit(0)
		} else {
			os.Exit(1)
		}
	case "vespa-deploy":
		cobra := deploy.NewDeployCmd()
		cobra.Execute()
	case "vespa-logfmt":
		cobra := logfmt.NewLogfmtCmd()
		cobra.Execute()
	case "vespa-get-cluster-state":
		cobra := clusterstate.NewGetClusterStateCmd()
		cobra.Execute()
	case "vespa-get-node-state":
		cobra := clusterstate.NewGetNodeStateCmd()
		cobra.Execute()
	case "vespa-set-node-state":
		cobra := clusterstate.NewSetNodeStateCmd()
		cobra.Execute()
	default:
		if startcbinary.IsCandidate(os.Args[0]) {
			os.Exit(startcbinary.Run(os.Args))
		}
		fmt.Fprintf(os.Stderr, "unknown action '%s'\n", action)
		fmt.Fprintln(os.Stderr, "actions: export-env, ipv6-only, security-env")
		fmt.Fprintln(os.Stderr, "(also: vespa-deploy, vespa-logfmt)")
	}
}

func handleSimplePanic() {
	if r := recover(); r != nil {
		if jee, ok := r.(*util.JustExitError); ok {
			fmt.Fprintln(os.Stderr, jee)
			os.Exit(1)
		} else {
			panic(r)
		}
	}
}
