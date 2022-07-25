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

// main entry point for vespa-logfmt

func RunLogfmt(opts *Options, args []string) {
	if len(args) == 0 {
		if inputIsTty() {
			args = append(args, vespaHome()+"/logs/vespa/vespa.log")
		} else {
			formatFile(opts, os.Stdin)
		}
	}
	if opts.FollowTail {
		if len(args) != 1 {
			fmt.Fprintf(os.Stderr, "Must have exact 1 file for 'follow' option, got %d\n", len(args))
			return
		}
		tailFile(opts, args[0])
		return
	}
	for _, arg := range args {
		file, err := os.Open(arg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Cannot open '%s': %v\n", arg, err)
		} else {
			formatFile(opts, file)
			file.Close()
		}
	}
}

func formatLine(opts *Options, line string) {
	output, err := handleLine(opts, line)
	if err != nil {
		fmt.Fprintln(os.Stderr, "bad log line:", err)
	} else {
		os.Stdout.WriteString(output)
	}
}

func tailFile(opts *Options, fn string) {
	tailed := FollowFile(fn)
	for line := range tailed.Lines {
		formatLine(opts, line.Text)
	}
}

func formatFile(opts *Options, arg *os.File) {
	input := bufio.NewScanner(arg)
	input.Buffer(make([]byte, 64*1024), 4*1024*1024)
	for input.Scan() {
		formatLine(opts, input.Text())
	}
}
