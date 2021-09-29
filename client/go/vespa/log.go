// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"bufio"
	"fmt"
	"io"
	"strconv"
	"strings"
	"time"
)

var dequoter = strings.NewReplacer("\\n", "\n", "\\t", "\t")

// LogEntry represents a Vespa log entry.
type LogEntry struct {
	Time      time.Time
	Host      string
	Service   string
	Component string
	Level     string
	Message   string
}

func (le *LogEntry) Format(dequote bool) string {
	t := le.Time.Format("2006-01-02 15:04:05.000000")
	msg := le.Message
	if dequote {
		msg = dequoter.Replace(msg)
	}
	return fmt.Sprintf("[%s] %-8s %-7s %-16s %s\t%s", t, le.Host, le.Level, le.Service, le.Component, msg)
}

// ParseLogEntry parses a Vespa log entry from string s.
func ParseLogEntry(s string) (LogEntry, error) {
	parts := strings.SplitN(s, "\t", 7)
	if len(parts) != 7 {
		return LogEntry{}, fmt.Errorf("invalid number of log parts: %d: %q", len(parts), s)
	}
	time, err := parseLogTimestamp(parts[0])
	if err != nil {
		return LogEntry{}, err
	}
	return LogEntry{
		Time:      time,
		Host:      parts[1],
		Service:   parts[3],
		Component: parts[4],
		Level:     parts[5],
		Message:   parts[6],
	}, nil
}

// ReadLogEntries reads and parses all log entries from reader r.
func ReadLogEntries(r io.Reader) ([]LogEntry, error) {
	var entries []LogEntry
	scanner := bufio.NewScanner(r)
	for scanner.Scan() {
		line := scanner.Text()
		logEntry, err := ParseLogEntry(line)
		if err != nil {
			return nil, err
		}
		entries = append(entries, logEntry)
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return entries, nil
}

// LogLevel returns an int representing a named log level.
func LogLevel(name string) int {
	switch name {
	case "error":
		return 0
	case "warning":
		return 1
	case "info":
		return 2
	default: // everything else, e.g. debug
		return 3
	}
}

func parseLogTimestamp(s string) (time.Time, error) {
	parts := strings.Split(s, ".")
	if len(parts) != 2 {
		return time.Time{}, fmt.Errorf("invalid number of log timestamp parts: %d", len(parts))
	}
	unixSecs, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid timestamp seconds: %s", parts[0])
	}
	unixMicros, err := strconv.ParseInt(parts[1], 10, 64)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid timestamp microseconds: %s", parts[1])
	}
	return time.Unix(unixSecs, unixMicros*1000).UTC(), nil
}
