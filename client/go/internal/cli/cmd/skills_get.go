// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/spf13/cobra"
)

const applicationSkillsPath = "/skills/v1"

func newSkillsGetCmd(cli *CLI) *cobra.Command {
	var (
		printCurlCmd bool
		waitSecs     int
	)
	cmd := &cobra.Command{
		Use:   "get [path]",
		Short: "Get the skills of a deployed application",
		Long: `Get the skills of a deployed application.

Applications can serve skills documenting themselves to agents, such as their
schemas, at /skills/v1/. This prints the document at the given path below it,
or the list of the application's skills if no path is given. The path may also
be given in full, as documents link to each other.

Unlike the other skills commands, this gets the skills of your application,
not the general Vespa skills from GitHub.`,
		Example: `$ vespa skills get
$ vespa skills get schemas
$ vespa skills get schemas/music
$ vespa skills get /skills/v1/schemas/music/`,
		Args:              cobra.MaximumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			path := ""
			if len(args) > 0 {
				path = args[0]
			}
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			return getApplicationSkill(cli, path, printCurlCmd, waiter)
		},
	}
	cmd.Flags().BoolVarP(&printCurlCmd, "verbose", "v", false, "Print the equivalent curl command")
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

// applicationSkillURLPath returns the URL path of the given skill path, which is either relative to /skills/v1/ or full.
func applicationSkillURLPath(path string) string {
	path = strings.Trim(path, "/")
	path = strings.TrimPrefix(path, strings.TrimPrefix(applicationSkillsPath, "/"))
	path = strings.Trim(path, "/")
	if path == "" {
		return applicationSkillsPath
	}
	return applicationSkillsPath + "/" + path
}

func getApplicationSkill(cli *CLI, path string, printCurlCmd bool, waiter *Waiter) error {
	target, err := cli.target(targetOptions{})
	if err != nil {
		return err
	}
	authMethod := cli.selectAuthMethod()
	service, err := waiter.ServiceWithAuthMethod(target, cli.config.cluster(), authMethod)
	if err != nil {
		return err
	}
	u, err := url.Parse(strings.TrimSuffix(service.BaseURL, "/") + applicationSkillURLPath(path))
	if err != nil {
		return err
	}
	header := http.Header{}
	if authMethod == "token" {
		if err := cli.addBearerToken(&header); err != nil {
			return err
		}
		service.TLSOptions.CertificateFile = ""
		service.TLSOptions.PrivateKeyFile = ""
	}
	request := &http.Request{Header: header, URL: u}
	if printCurlCmd {
		if err := printCurl(cli.Stderr, request, "", service); err != nil {
			return err
		}
	}
	response, err := service.Do(request, 10*time.Second)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return err
	}
	switch {
	case response.StatusCode == 200:
		_, err := cli.Stdout.Write(body)
		return err
	case response.StatusCode == 404 && path == "":
		return errHint(fmt.Errorf("%s: this application serves no skills", response.Status),
			"Enable the schemas skill by adding <skills><schemas/></skills> to the container cluster in services.xml")
	default:
		return fmt.Errorf("%s getting %s\n%s", response.Status, u.Path, string(body))
	}
}
