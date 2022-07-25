// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/cmd/logfmt"
)

func newLogfmtCmd(cli *CLI) *cobra.Command {
	return logfmt.NewLogfmtCommand()
}
