package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestConfig(t *testing.T) {
	assert.Equal(t, "invalid option or value: \"foo\": \"bar\"\n", executeCommand(t, nil, []string{"config", "set", "foo", "bar"}, nil))
	assert.Equal(t, "foo = <unset>\n", executeCommand(t, nil, []string{"config", "get", "foo"}, nil))
	assert.Equal(t, "target = local\n", executeCommand(t, nil, []string{"config", "get", "target"}, nil))
	assert.Equal(t, "", executeCommand(t, nil, []string{"config", "set", "target", "cloud"}, nil))
	assert.Equal(t, "target = cloud\n", executeCommand(t, nil, []string{"config", "get", "target"}, nil))
	assert.Equal(t, "", executeCommand(t, nil, []string{"config", "set", "target", "http://127.0.0.1:8080"}, nil))
	assert.Equal(t, "", executeCommand(t, nil, []string{"config", "set", "target", "https://127.0.0.1"}, nil))
	assert.Equal(t, "target = https://127.0.0.1\n", executeCommand(t, nil, []string{"config", "get", "target"}, nil))

	assert.Equal(t, "invalid application: \"foo\"\n", executeCommand(t, nil, []string{"config", "set", "application", "foo"}, nil))
	assert.Equal(t, "application = <unset>\n", executeCommand(t, nil, []string{"config", "get", "application"}, nil))
	assert.Equal(t, "", executeCommand(t, nil, []string{"config", "set", "application", "t1.a1.i1"}, nil))
	assert.Equal(t, "application = t1.a1.i1\n", executeCommand(t, nil, []string{"config", "get", "application"}, nil))

	assert.Equal(t, "target = https://127.0.0.1\napplication = t1.a1.i1\n", executeCommand(t, nil, []string{"config", "get"}, nil))
}
