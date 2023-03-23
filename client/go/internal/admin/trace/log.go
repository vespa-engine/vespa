// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// handling of informational output
package trace

import (
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
)

func getComponent() string {
	s := os.Args[0]
	parts := strings.Split(s, "/")
	return parts[len(parts)-1]
}

// make a vespa-format log line

func logMessage(l outputLevel, msg string) {
	out := os.Stderr
	unixTime := float64(time.Now().UnixMicro()) * 1.0e-6
	hostname := os.Getenv(envvars.VESPA_HOSTNAME)
	pid := os.Getpid()
	service := os.Getenv(envvars.VESPA_SERVICE_NAME)
	if service == "" {
		service = "-"
	}
	component := getComponent()
	level := "error"
	switch l {
	case levelWarning:
		level = "warning"
	case levelInfo:
		level = "info"
	case levelTrace:
		level = "info"
		msg = fmt.Sprintf("[trace] %s", msg)
	case levelDebug:
		level = "debug"
	case levelSpam:
		level = "spam"
	}
	if !strings.HasSuffix(msg, "\n") {
		msg = msg + "\n"
	}
	fmt.Fprintf(out, "%.6f\t%s\t%d\t%s\t%s\t%s\t%s",
		unixTime, hostname, pid, service, component, level, msg)
}

func LogInfo(msg string) {
	logMessage(levelInfo, msg)
}

func LogDebug(msg string) {
	logMessage(levelDebug, msg)
}

func LogWarning(msg string) {
	logMessage(levelWarning, msg)
}
