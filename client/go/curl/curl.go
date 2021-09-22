// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package curl

import (
	"io"
	"net/url"
	"os/exec"
	"runtime"

	"github.com/kballard/go-shellquote"
)

type header struct {
	key   string
	value string
}

type Command struct {
	Path        string
	Method      string
	PrivateKey  string
	Certificate string
	BodyFile    string
	url         *url.URL
	headers     []header
	rawArgs     []string
}

func (c *Command) Args() []string {
	var args []string
	if c.PrivateKey != "" {
		args = append(args, "--key", c.PrivateKey)
	}
	if c.Certificate != "" {
		args = append(args, "--cert", c.Certificate)
	}
	if c.Method != "" {
		args = append(args, "-X", c.Method)
	}
	for _, header := range c.headers {
		args = append(args, "-H", header.key+": "+header.value)
	}
	if c.BodyFile != "" {
		args = append(args, "--data-binary", "@"+c.BodyFile)
	}
	args = append(args, c.rawArgs...)
	args = append(args, c.url.String())
	return args
}

func (c *Command) String() string {
	args := []string{c.Path}
	args = append(args, c.Args()...)
	return shellquote.Join(args...)
}

func (c *Command) Header(key, value string) {
	c.headers = append(c.headers, header{key: key, value: value})
}

func (c *Command) Param(key, value string) {
	query := c.url.Query()
	query.Set(key, value)
	c.url.RawQuery = query.Encode()
}

func (c *Command) Run(stdout, stderr io.Writer) error {
	cmd := exec.Command(c.Path, c.Args()...)
	cmd.Stdout = stdout
	cmd.Stderr = stderr
	if err := cmd.Start(); err != nil {
		return err
	}
	return cmd.Wait()
}

func Post(url string) (*Command, error) { return curl("POST", url) }

func Get(url string) (*Command, error) { return curl("", url) }

func RawArgs(url string, args ...string) (*Command, error) {
	c, err := curl("", url)
	if err != nil {
		return nil, err
	}
	c.rawArgs = args
	return c, nil
}

func curl(method, rawurl string) (*Command, error) {
	path := "curl"
	if runtime.GOOS == "windows" {
		path = "curl.exe"
	}
	realURL, err := url.Parse(rawurl)
	if err != nil {
		return nil, err
	}
	return &Command{
		Path:   path,
		Method: method,
		url:    realURL,
	}, nil
}
