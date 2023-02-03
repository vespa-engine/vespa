// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// code for the "vespa-get-node-state" command
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"fmt"
	"os"
	"strconv"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/cli/build"
)

const (
	longdesc = `Retrieve the state of one or more storage services from the fleet controller. Will list the state of the locally running services, possibly restricted to less by options.`
	header   = `Shows the various states of one or more nodes in a Vespa Storage cluster. There exist three different type of node states. They are:

  Unit state      - The state of the node seen from the cluster controller.
  User state      - The state we want the node to be in. By default up. Can be
                    set by administrators or by cluster controller when it
                    detects nodes that are behaving badly.
  Generated state - The state of a given node in the current cluster state.
                    This is the state all the other nodes know about. This
                    state is a product of the other two states and cluster
                    controller logic to keep the cluster stable.`
)

func NewGetNodeStateCmd() *cobra.Command {
	var (
		curOptions Options
	)
	cmd := &cobra.Command{
		Use:               "vespa-get-node-state [-h] [-v] [-c cluster] [-t type] [-i index]",
		Short:             "Get the state of a node.",
		Long:              longdesc + "\n\n" + header,
		Version:           build.Version,
		Args:              cobra.MaximumNArgs(0),
		CompletionOptions: cobra.CompletionOptions{DisableDefaultCmd: true},
		Run: func(cmd *cobra.Command, args []string) {
			runGetNodeState(&curOptions)
		},
	}
	addCommonOptions(cmd, &curOptions)
	cmd.Flags().StringVarP(&curOptions.NodeType, "type", "t", "",
		"Node type - can either be 'storage' or 'distributor'. If not specified, the operation will use state for both types.")
	cmd.Flags().IntVarP(&curOptions.NodeIndex, "index", "i", OnlyLocalNode,
		"Node index. If not specified, all nodes found running on this host will be used.")
	return cmd
}

func runGetNodeState(opts *Options) {
	if opts.Silent {
		trace.Silent()
	}
	if opts.NoColors || os.Getenv(envvars.TERM) == "" {
		color.NoColor = true
	}
	trace.Info(header)
	m := detectModel(opts)
	sss := m.findSelectedServices(opts)
	clusters := make(map[string]*ClusterState)
	for _, s := range sss {
		state := clusters[s.cluster]
		if state == nil {
			state, _ = m.getClusterState(s.cluster)
			clusters[s.cluster] = state
		}
		if state == nil {
			trace.Warning("no state for cluster: ", s.cluster)
			continue
		}
		if nodes, ok := state.Service[s.serviceType]; ok {
			for name, node := range nodes.Node {
				if name == strconv.Itoa(s.index) {
					fmt.Printf("\n%s/%s.%s:\n", s.cluster, s.serviceType, name)
					dumpState(node.State.Unit, "Unit")
					dumpState(node.State.Generated, "Generated")
					dumpState(node.State.User, "User")
				}
			}
		} else {
			trace.Warning("no nodes for service type: ", s.serviceType)
			continue
		}

	}
}

func dumpState(s StateAndReason, tag string) {
	if s.State != "up" {
		s.State = color.HiRedString(s.State)
	}
	fmt.Printf("%s: %s: %s\n", tag, s.State, s.Reason)
}
