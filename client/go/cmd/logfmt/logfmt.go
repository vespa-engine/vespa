// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/mattn/go-isatty"
)

type Options struct {
	ShowFields        flagValueForShow
	ShowLevels        flagValueForLevel
	OnlyHostname      string
	OnlyPid           string
	OnlyService       string
	OnlyInternal      bool
	FollowTail        bool
	DequoteNewlines   bool
	TruncateService   bool
	TruncateComponent bool
	ComponentFilter   regexFlag
	MessageFilter     regexFlag
}

func NewOptions() (ret Options) {
	ret.ShowLevels.levels = defaultLevelFlags()
	ret.ShowFields.shown = defaultShowFlags()
	return
}

func (o *Options) showField(field string) bool {
	return o.ShowFields.shown[field]
}

func (o *Options) showLevel(level string) bool {
	rv, ok := o.ShowLevels.levels[level]
	if !ok {
		o.ShowLevels.levels[level] = true
		fmt.Fprintf(os.Stderr, "Warnings: unknown level '%s' in input\n", level)
		return true
	}
	return rv
}

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
		output, err := handle(opts, input.Text())
		if err != nil {
			fmt.Fprintln(os.Stdout, "bad log line:", err)
		} else {
			stdout.WriteString(output)
		}
	}
}

func handle(opts *Options, line string) (output string, err error) {
	fields := strings.SplitN(line, "\t", 7)
	if len(fields) < 7 {
		return "", fmt.Errorf("not enough fields: '%s'", line)
	}
	timestampfield := fields[0] // seconds, optional fractional seconds
	hostfield := fields[1]
	pidfield := fields[2] // pid, optional tid
	servicefield := fields[3]
	componentfield := fields[4]
	levelfield := fields[5]
	messagefields := fields[6:]

	if !opts.showLevel(levelfield) {
		return "", nil
	}
	if opts.OnlyHostname != "" && opts.OnlyHostname != hostfield {
		return "", nil
	}
	if opts.OnlyPid != "" && opts.OnlyPid != pidfield {
		return "", nil
	}
	if opts.OnlyService != "" && opts.OnlyService != servicefield {
		return "", nil
	}
	if opts.OnlyInternal && !isInternal(componentfield) {
		return "", nil
	}
	if opts.ComponentFilter.unmatched(componentfield) {
		return "", nil
	}
	if opts.MessageFilter.unmatched(strings.Join(messagefields, "\t")) {
		return "", nil
	}

	var buf strings.Builder

	if opts.showField("fmttime") {
		secs, err := strconv.ParseFloat(timestampfield, 64)
		if err != nil {
			return "", err
		}
		nsecs := int64(secs * 1e9)
		timestamp := time.Unix(0, nsecs)
		if opts.showField("usecs") {
			buf.WriteString(timestamp.Format("[2006-01-02 15:04:05.000000] "))
		} else if opts.showField("msecs") {
			buf.WriteString(timestamp.Format("[2006-01-02 15:04:05.000] "))
		} else {
			buf.WriteString(timestamp.Format("[2006-01-02 15:04:05] "))
		}
	} else if opts.showField("time") {
		buf.WriteString(timestampfield)
		buf.WriteString(" ")
	}
	if opts.showField("host") {
		buf.WriteString(fmt.Sprintf("%-8s ", hostfield))
	}
	if opts.showField("level") {
		buf.WriteString(fmt.Sprintf("%-7s ", strings.ToUpper(levelfield)))
	}
	if opts.showField("pid") {
		// OnlyPid, _, _ := strings.Cut(pidfield, "/")
		buf.WriteString(fmt.Sprintf("%6s ", pidfield))
	}
	if opts.showField("service") {
		if opts.TruncateService {
			buf.WriteString(fmt.Sprintf("%-9.9s ", servicefield))
		} else {
			buf.WriteString(fmt.Sprintf("%-16s ", servicefield))
		}
	}
	if opts.showField("component") {
		if opts.TruncateComponent {
			buf.WriteString(fmt.Sprintf("%-15.15s ", componentfield))
		} else {
			buf.WriteString(fmt.Sprintf("%s\t", componentfield))
		}
	}
	if opts.showField("message") {
		var msgBuf strings.Builder
		for idx, message := range messagefields {
			if idx > 0 {
				msgBuf.WriteString("\n\t")
			}
			if opts.DequoteNewlines {
				message = strings.ReplaceAll(message, "\\n\\t", "\n\t")
				message = strings.ReplaceAll(message, "\\n", "\n\t")
			}
			msgBuf.WriteString(message)
		}
		message := msgBuf.String()
		if strings.Contains(message, "\n") {
			buf.WriteString("\n\t")
		}
		buf.WriteString(message)
	}
	buf.WriteString("\n")
	output = buf.String()
	return
}
