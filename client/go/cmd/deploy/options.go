// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa-deploy command
// Author: arnej

package deploy

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
