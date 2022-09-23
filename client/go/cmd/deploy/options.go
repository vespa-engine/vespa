// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

import (
	"strconv"
	"strings"
)

type CmdType int

const (
	CmdNone CmdType = iota
	CmdUpload
	CmdPrepare
	CmdActivate
	CmdFetch
)

type Options struct {
	Command CmdType

	Verbose bool
	DryRun  bool
	Force   bool
	Hosted  bool

	Application  string
	Environment  string
	From         string
	Instance     string
	Region       string
	Rotations    string
	ServerHost   string
	Tenant       string
	VespaVersion string

	Timeout    int
	PortNumber int
}

func (opts *Options) String() string {
	var buf strings.Builder
	buf.WriteString("command-line options [")
	if opts.DryRun {
		buf.WriteString(" dry-run")
	}
	if opts.Force {
		buf.WriteString(" force")
	}
	if opts.Hosted {
		buf.WriteString(" hosted")
	}
	if opts.ServerHost != "" {
		buf.WriteString(" server=")
		buf.WriteString(opts.ServerHost)
	}
	if opts.PortNumber != 19071 {
		buf.WriteString(" port=")
		buf.WriteString(strconv.Itoa(opts.PortNumber))
	}
	if opts.From != "" {
		buf.WriteString(" from=")
		buf.WriteString(opts.From)
	}
	buf.WriteString(" ]")
	return buf.String()
}
