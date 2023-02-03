// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"strconv"
	"strings"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	OnlyLocalNode int = -2
	AllNodes      int = -1
)

type Options struct {
	Verbose              int
	Silent               bool
	ShowHidden           showHiddenFlag
	Force                bool
	NoColors             bool
	SafeMode             bool
	NoWait               bool
	Cluster              string
	ConfigServerHost     string
	ConfigServerPort     int
	ConfigRequestTimeout int
	NodeType             string
	NodeIndex            int
	WantedState          string
}

func (v *Options) String() string {
	var buf strings.Builder
	buf.WriteString("command-line options [")
	if v.Verbose > 0 {
		buf.WriteString(" verbosity=")
		buf.WriteString(strconv.Itoa(v.Verbose))
	}
	if v.Silent {
		buf.WriteString(" silent")
	}
	if v.ShowHidden.showHidden {
		buf.WriteString(" show-hidden")
	}
	if v.Force {
		buf.WriteString(color.HiYellowString(" force=true"))
	}
	if v.NoColors {
		buf.WriteString(" no-colors")
	}
	if v.SafeMode {
		buf.WriteString(" safe-mode")
	}
	if v.NoWait {
		buf.WriteString(color.HiYellowString(" no-wait=true"))
	}
	if v.Cluster != "" {
		buf.WriteString(" cluster=")
		buf.WriteString(v.Cluster)
	}
	if v.ConfigServerHost != "" {
		buf.WriteString(" config-server=")
		buf.WriteString(v.ConfigServerHost)
	}
	if v.ConfigServerPort != 0 {
		buf.WriteString(" config-server-port=")
		buf.WriteString(strconv.Itoa(v.ConfigServerPort))
	}
	if v.ConfigRequestTimeout != 90 {
		buf.WriteString(" config-request-timeout=")
		buf.WriteString(strconv.Itoa(v.ConfigRequestTimeout))
	}
	if v.NodeType != "" {
		buf.WriteString(" node-type=")
		buf.WriteString(v.NodeType)
	}
	if v.NodeIndex >= 0 {
		buf.WriteString(" node-index=")
		buf.WriteString(strconv.Itoa(int(v.NodeIndex)))
	}
	if v.WantedState != "" {
		buf.WriteString(" WantedState=")
		buf.WriteString(v.WantedState)
	}
	buf.WriteString(" ]")
	return buf.String()
}

type serviceSpec struct {
	cluster     string
	serviceType string
	index       int
	host        string
}

func (o *Options) wantService(s serviceSpec) bool {
	if o.Cluster != "" && o.Cluster != s.cluster {
		return false
	}
	if o.NodeType == "" {
		if s.serviceType != "storage" && s.serviceType != "distributor" {
			return false
		}
	} else if o.NodeType != s.serviceType {
		return false
	}
	switch o.NodeIndex {
	case OnlyLocalNode:
		myName, _ := vespa.FindOurHostname()
		return s.host == "localhost" || s.host == myName
	case AllNodes:
		return true
	case s.index:
		return true
	default:
		return false
	}
}

func addCommonOptions(cmd *cobra.Command, curOptions *Options) {
	cmd.Flags().BoolVar(&curOptions.NoColors, "nocolors", false, "Do not use ansi colors in print.")
	cmd.Flags().BoolVarP(&curOptions.Silent, "silent", "s", false, "Create less verbose output.")
	cmd.Flags().CountVarP(&curOptions.Verbose, "verbose", "v", "Create more verbose output.")
	cmd.Flags().IntVar(&curOptions.ConfigRequestTimeout, "config-request-timeout", 90, "Timeout of config request")
	cmd.Flags().IntVar(&curOptions.ConfigServerPort, "config-server-port", 0, "Port to connect to config server on")
	cmd.Flags().StringVar(&curOptions.ConfigServerHost, "config-server", "", "Host name of config server to query")
	cmd.Flags().StringVarP(&curOptions.Cluster, "cluster", "c", "",
		"Cluster name. If unspecified, and vespa is installed on current node, information will be attempted auto-extracted")
	cmd.Flags().MarkHidden("config-request-timeout")
	cmd.Flags().MarkHidden("config-server-port")
	cmd.Flags().MarkHidden("nocolors")
	curOptions.ShowHidden.cmd = cmd
	flag := cmd.Flags().VarPF(&curOptions.ShowHidden, "show-hidden", "", "Also show hidden undocumented debug options.")
	flag.NoOptDefVal = "true"
	cobra.OnInitialize(func() {
		if curOptions.Silent {
			trace.Silent()
		} else {
			trace.AdjustVerbosity(curOptions.Verbose)
		}
	})
}
