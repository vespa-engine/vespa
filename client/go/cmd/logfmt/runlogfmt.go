// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"bufio"
	"fmt"
	"os"

	"github.com/mattn/go-isatty"
)

func inputIsTty() bool {
	return isatty.IsTerminal(os.Stdin.Fd())
}

func vespaHome() string {
	ev := os.Getenv("VESPA_HOME")
	if ev == "" {
		return "/opt/vespa"
	}
	return ev
}

func RunLogfmt(opts *Options, args []string) {
	if len(args) == 0 {
		if inputIsTty() {
			args = append(args, vespaHome()+"/logs/vespa/vespa.log")
		} else {
			formatFile(opts, os.Stdin)
		}
	}
	for _, arg := range args {
		file, err := os.Open(arg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Cannot open '%s': %v", arg, err)
		} else {
			formatFile(opts, file)
		}
	}
}

func formatFile(opts *Options, arg *os.File) {
	stdout := os.Stdout
	input := bufio.NewScanner(arg)
	input.Buffer(make([]byte, 64*1024), 4*1024*1024)
	for input.Scan() {
		output, err := handleLine(opts, input.Text())
		if err != nil {
			fmt.Fprintln(os.Stdout, "bad log line:", err)
		} else {
			stdout.WriteString(output)
		}
	}
}
