package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestConfig(t *testing.T) {
	configDir := t.TempDir()
	assert.Equal(t, "invalid option or value: \"foo\": \"bar\"\n", execute(command{configDir: configDir, args: []string{"config", "set", "foo", "bar"}}, t, nil))
	assert.Equal(t, "foo = <unset>\n", execute(command{configDir: configDir, args: []string{"config", "get", "foo"}}, t, nil))
	assert.Equal(t, "target = local\n", execute(command{configDir: configDir, args: []string{"config", "get", "target"}}, t, nil))
	assert.Equal(t, "", execute(command{configDir: configDir, args: []string{"config", "set", "target", "cloud"}}, t, nil))
	assert.Equal(t, "target = cloud\n", execute(command{configDir: configDir, args: []string{"config", "get", "target"}}, t, nil))
	assert.Equal(t, "", execute(command{configDir: configDir, args: []string{"config", "set", "target", "http://127.0.0.1:8080"}}, t, nil))
	assert.Equal(t, "", execute(command{configDir: configDir, args: []string{"config", "set", "target", "https://127.0.0.1"}}, t, nil))
	assert.Equal(t, "target = https://127.0.0.1\n", execute(command{configDir: configDir, args: []string{"config", "get", "target"}}, t, nil))

	assert.Equal(t, "invalid application: \"foo\"\n", execute(command{configDir: configDir, args: []string{"config", "set", "application", "foo"}}, t, nil))
	assert.Equal(t, "application = <unset>\n", execute(command{configDir: configDir, args: []string{"config", "get", "application"}}, t, nil))
	assert.Equal(t, "", execute(command{configDir: configDir, args: []string{"config", "set", "application", "t1.a1.i1"}}, t, nil))
	assert.Equal(t, "application = t1.a1.i1\n", execute(command{configDir: configDir, args: []string{"config", "get", "application"}}, t, nil))

	assert.Equal(t, "target = https://127.0.0.1\napplication = t1.a1.i1\n", execute(command{configDir: configDir, args: []string{"config", "get"}}, t, nil))
}
