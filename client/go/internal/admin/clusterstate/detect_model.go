// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func getConfigServerHosts(s string) []string {
	if s != "" {
		return []string{s}
	}
	backticks := util.BackTicksForwardStderr
	got, err := backticks.Run(vespa.FindHome()+"/bin/vespa-print-default", "configservers")
	res := strings.Fields(got)
	if err != nil || len(res) < 1 {
		util.JustExitMsg("bad configservers: " + got)
	}
	trace.Debug("found", len(res), "configservers:", res)
	return res
}

func getConfigServerPort(i int) int {
	if i > 0 {
		return i
	}
	backticks := util.BackTicksForwardStderr
	got, err := backticks.Run(vespa.FindHome()+"/bin/vespa-print-default", "configserver_rpc_port")
	if err == nil {
		i, err = strconv.Atoi(strings.TrimSpace(got))
	}
	if err != nil || i < 1 {
		util.JustExitMsg("bad configserver_rpc_port: " + got)
	}
	trace.Debug("found configservers rpc port:", i)
	return i
}

func detectModel(opts *Options) *VespaModelConfig {
	vespa.LoadDefaultEnv()
	cfgHosts := getConfigServerHosts(opts.ConfigServerHost)
	cfgPort := getConfigServerPort(opts.ConfigServerPort)
	for _, cfgHost := range cfgHosts {
		args := []string{
			"-j",
			"-n", "cloud.config.model",
			"-i", "admin/model",
			"-p", strconv.Itoa(cfgPort),
			"-s", cfgHost,
		}
		backticks := util.BackTicksForwardStderr
		data, err := backticks.Run(vespa.FindHome()+"/bin/vespa-get-config", args...)
		parsed := parseModelConfig(data)
		if err == nil && parsed != nil {
			return parsed
		}
	}
	util.JustExitMsg("could not get model config")
	panic("unreachable")
}
