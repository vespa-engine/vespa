// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"fmt"
	"strconv"
	"strings"
	"time"
)

func handleLine(opts *Options, line string) (output string, err error) {
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
