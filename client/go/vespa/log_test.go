// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestParseLogEntry(t *testing.T) {
	expected := LogEntry{
		Time:      time.Date(2021, 9, 27, 10, 31, 30, 905535000, time.UTC),
		Host:      "host1a.dev.aws-us-east-1c",
		Service:   "logserver-container",
		Component: "Container.com.yahoo.container.jdisc.ConfiguredApplication",
		Level:     "info",
		Message:   "Switching to the latest deployed set of configurations and components. Application config generation: 52532",
	}
	in := "1632738690.905535	host1a.dev.aws-us-east-1c	806/53	logserver-container	Container.com.yahoo.container.jdisc.ConfiguredApplication	info	Switching to the latest deployed set of configurations and components. Application config generation: 52532"
	logEntry, err := ParseLogEntry(in)
	assert.Nil(t, err)
	assert.Equal(t, expected, logEntry)

	formatted := "[2021-09-27 10:31:30.905535] host1a.dev.aws-us-east-1c info    logserver-container Container.com.yahoo.container.jdisc.ConfiguredApplication\tSwitching to the latest deployed set of configurations and components. Application config generation: 52532"
	assert.Equal(t, formatted, logEntry.Format(false))

	in = "1632738690.905535	host1a.dev.aws-us-east-1c	806/53	logserver-container	Container.com.yahoo.container.jdisc.ConfiguredApplication	info	message containing newline\\nand\\ttab"
	logEntry, err = ParseLogEntry(in)
	assert.Nil(t, err)
	assert.Equal(t, "[2021-09-27 10:31:30.905535] host1a.dev.aws-us-east-1c info    logserver-container Container.com.yahoo.container.jdisc.ConfiguredApplication\tmessage containing newline\nand\ttab", logEntry.Format(true))
}
