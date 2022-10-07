// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"bytes"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"
)

type logFields struct {
	timestamp string // seconds, optional fractional seconds
	host      string
	pid       string // pid, optional tid
	service   string
	component string
	level     string
	messages  []string
}

// handle a line in "vespa.log" format; do filtering and formatting as specified in opts
func handleLine(opts *Options, line string) (string, error) {
	fieldStrings := strings.SplitN(line, "\t", 7)
	if len(fieldStrings) < 7 {
		return "", fmt.Errorf("not enough fields: '%s'", line)
	}
	fields := logFields{
		timestamp: fieldStrings[0],
		host:      fieldStrings[1],
		pid:       fieldStrings[2],
		service:   fieldStrings[3],
		component: fieldStrings[4],
		level:     fieldStrings[5],
		messages:  fieldStrings[6:],
	}

	if !opts.showLevel(fields.level) {
		return "", nil
	}
	if opts.OnlyHostname != "" && opts.OnlyHostname != fields.host {
		return "", nil
	}
	if opts.OnlyPid != "" && opts.OnlyPid != fields.pid {
		return "", nil
	}
	if opts.OnlyService != "" && opts.OnlyService != fields.service {
		return "", nil
	}
	if opts.OnlyInternal && !isInternal(fields.component) {
		return "", nil
	}
	if opts.ComponentFilter.unmatched(fields.component) {
		return "", nil
	}
	if opts.MessageFilter.unmatched(strings.Join(fields.messages, "\t")) {
		return "", nil
	}

	switch opts.Format {
	case FormatRaw:
		return line + "\n", nil
	case FormatJSON:
		return handleLineJson(opts, &fields)
	case FormatVespa:
		fallthrough
	default:
		return handleLineVespa(opts, &fields)
	}
}

func parseTimestamp(timestamp string) (time.Time, error) {
	secs, err := strconv.ParseFloat(timestamp, 64)
	if err != nil {
		return time.Time{}, err
	}
	nsecs := int64(secs * 1e9)
	return time.Unix(0, nsecs), nil
}

type logFieldsJson struct {
	Timestamp string   `json:"timestamp"`
	Host      string   `json:"host"`
	Pid       string   `json:"pid"`
	Service   string   `json:"service"`
	Component string   `json:"component"`
	Level     string   `json:"level"`
	Messages  []string `json:"messages"`
}

func handleLineJson(_ *Options, fields *logFields) (string, error) {
	timestamp, err := parseTimestamp(fields.timestamp)
	if err != nil {
		return "", err
	}
	outputFields := logFieldsJson{
		Timestamp: timestamp.Format(time.RFC3339Nano),
		Host:      fields.host,
		Pid:       fields.pid,
		Service:   fields.service,
		Component: fields.component,
		Level:     fields.level,
		Messages:  fields.messages,
	}
	buf := bytes.Buffer{}
	if err := json.NewEncoder(&buf).Encode(&outputFields); err != nil {
		return "", err
	}
	return buf.String(), nil
}

func handleLineVespa(opts *Options, fields *logFields) (string, error) {
	var buf strings.Builder

	if opts.showField("fmttime") {
		timestamp, err := parseTimestamp(fields.timestamp)
		if err != nil {
			return "", err
		}
		if opts.showField("usecs") {
			buf.WriteString(timestamp.Format("[2006-01-02 15:04:05.000000] "))
		} else if opts.showField("msecs") {
			buf.WriteString(timestamp.Format("[2006-01-02 15:04:05.000] "))
		} else {
			buf.WriteString(timestamp.Format("[2006-01-02 15:04:05] "))
		}
	} else if opts.showField("time") {
		buf.WriteString(fields.timestamp)
		buf.WriteString(" ")
	}
	if opts.showField("host") {
		buf.WriteString(fmt.Sprintf("%-8s ", fields.host))
	}
	if opts.showField("level") {
		buf.WriteString(fmt.Sprintf("%-7s ", strings.ToUpper(fields.level)))
	}
	if opts.showField("pid") {
		// OnlyPid, _, _ := strings.Cut(pidfield, "/")
		buf.WriteString(fmt.Sprintf("%6s ", fields.pid))
	}
	if opts.showField("service") {
		if opts.TruncateService {
			buf.WriteString(fmt.Sprintf("%-9.9s ", fields.service))
		} else {
			buf.WriteString(fmt.Sprintf("%-16s ", fields.service))
		}
	}
	if opts.showField("component") {
		if opts.TruncateComponent {
			buf.WriteString(fmt.Sprintf("%-15.15s ", fields.component))
		} else {
			buf.WriteString(fmt.Sprintf("%s\t", fields.component))
		}
	}
	if opts.showField("message") {
		var msgBuf strings.Builder
		for idx, message := range fields.messages {
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
	return buf.String(), nil
}
