// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// code for the "vespa-set-node-state" command
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/build"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

const (
	usageSetNodeState = `vespa-set-node-state  [Options] <Wanted State> [Description]

Arguments:
 Wanted State : User state to set. This must be one of up, down, maintenance or retired.
 Description  : Give a reason for why you are altering the user state, which will show up in various admin tools. (Use double quotes to give a reason
                with whitespace in it)`

	longSetNodeState = `Set the user state of a node. This will set the generated state to the user state if the user state is "better" than the generated state that would
have been created if the user state was up. For instance, a node that is currently in initializing state can be forced into down state, while a node
that is currently down can not be forced into retired state, but can be forced into maintenance state.`
)

func NewSetNodeStateCmd() *cobra.Command {
	var (
		curOptions Options
	)
	cmd := &cobra.Command{
		Use:     usageSetNodeState,
		Short:   "vespa-set-node-state [Options] <Wanted State> [Description]",
		Long:    longSetNodeState,
		Version: build.Version,
		Args: func(cmd *cobra.Command, args []string) error {
			switch {
			case len(args) < 1:
				return fmt.Errorf("Missing <Wanted State>")
			case len(args) > 2:
				return fmt.Errorf("Too many arguments, maximum is 2")
			}
			_, err := knownState(args[0])
			return err
		},
		CompletionOptions: cobra.CompletionOptions{DisableDefaultCmd: true},
		Run: func(cmd *cobra.Command, args []string) {
			runSetNodeState(&curOptions, args)
		},
	}
	addCommonOptions(cmd, &curOptions)
	cmd.Flags().BoolVarP(&curOptions.Force, "force", "f", false,
		"Force execution")
	cmd.Flags().BoolVarP(&curOptions.NoWait, "no-wait", "n", false,
		"Do not wait for node state changes to be visible in the cluster before returning.")
	cmd.Flags().BoolVarP(&curOptions.SafeMode, "safe", "a", false,
		"Only carries out state changes if deemed safe by the cluster controller.")
	cmd.Flags().StringVarP(&curOptions.NodeType, "type", "t", "",
		"Node type - can either be 'storage' or 'distributor'. If not specified, the operation will set state for both types.")
	cmd.Flags().IntVarP(&curOptions.NodeIndex, "index", "i", OnlyLocalNode,
		"Node index. If not specified, all nodes found running on this host will be used.")
	cmd.Flags().MarkHidden("no-wait")
	return cmd
}

func runSetNodeState(opts *Options, args []string) {
	if opts.Silent {
		trace.Silent()
	}
	if opts.NoColors || os.Getenv(envvars.TERM) == "" {
		color.NoColor = true
	}
	wanted, err := knownState(args[0])
	if err != nil {
		util.JustExitWith(err)
	}
	reason := ""
	if len(args) > 1 {
		reason = args[1]
	}
	if !opts.Force && wanted == StateMaintenance && opts.NodeType != "storage" {
		fmt.Println(color.HiYellowString(
			`Setting the distributor to maintenance mode may have severe consequences for feeding!
Please specify -t storage to only set the storage node to maintenance mode, or -f to override this error.`))
		return
	}
	m := detectModel(opts)
	sss := m.findSelectedServices(opts)
	if len(sss) == 0 {
		fmt.Println(color.HiYellowString("Attempted setting of user state for no nodes"))
		return
	}
	for _, s := range sss {
		_, cc := m.getClusterState(s.cluster)
		cc.setNodeUserState(s, wanted, reason, opts)
	}
}

type SetNodeStateJson struct {
	State struct {
		User StateAndReason `json:"user"`
	} `json:"state"`
	ResponseWait string `json:"response-wait,omitempty"`
	Condition    string `json:"condition,omitempty"`
}

func splitResultCode(s string) (int, string) {
	for idx := len(s); idx > 0; {
		idx--
		if s[idx] == '\n' {
			resCode, err := strconv.Atoi(s[idx+1:])
			if err != nil {
				return -1, s
			}
			return resCode, s[:idx]
		}
	}
	return -1, s
}

func (cc *ClusterControllerSpec) setNodeUserState(s serviceSpec, wanted KnownState, reason string, opts *Options) error {
	var request SetNodeStateJson
	request.State.User.State = string(wanted)
	request.State.User.Reason = reason
	if opts.NoWait {
		request.ResponseWait = "no-wait"
	}
	if opts.SafeMode {
		request.Condition = "safe"
	}
	jsonBytes, err := json.Marshal(request)
	if err != nil {
		util.JustExitWith(err)
	}
	url := fmt.Sprintf("http://%s:%d/cluster/v2/%s/%s/%d",
		cc.host, cc.port,
		s.cluster, s.serviceType, s.index)
	result, err := curlPost(url, jsonBytes)
	resCode, output := splitResultCode(result)
	if resCode < 200 || resCode >= 300 {
		fmt.Println(color.HiYellowString("failed with HTTP code %d", resCode))
		fmt.Println(output)
	} else {
		fmt.Print(output, "OK\n")
	}
	return err
}
