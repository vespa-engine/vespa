// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth/auth0"
	"github.com/vespa-engine/vespa/client/go/curl"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func newCurlCmd(cli *CLI) *cobra.Command {
	var (
		dryRun      bool
		curlService string
	)
	cmd := &cobra.Command{
		Use:   "curl [curl-options] path",
		Short: "Access Vespa directly using curl",
		Long: `Access Vespa directly using curl.

Execute curl with the appropriate URL, certificate and private key for your application.

For a more high-level interface to query and feeding, see the 'query' and 'document' commands.
`,
		Example: `$ vespa curl /ApplicationStatus
$ vespa curl -- -X POST -H "Content-Type:application/json" --data-binary @src/test/resources/A-Head-Full-of-Dreams.json /document/v1/namespace/music/docid/1
$ vespa curl -- -v --data-urlencode "yql=select * from music where album contains 'head';" /search/\?hits=5`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			service, err := target.Service(curlService, 0, 0, "")
			if err != nil {
				return err
			}
			url := joinURL(service.BaseURL, args[len(args)-1])
			rawArgs := args[:len(args)-1]
			c, err := curl.RawArgs(url, rawArgs...)
			if err != nil {
				return err
			}
			switch curlService {
			case vespa.DeployService:
				if target.Type() == vespa.TargetCloud {
					if err := addCloudAuth0Authentication(target.Deployment().System, cli.config, c); err != nil {
						return err
					}
				}
			case vespa.DocumentService, vespa.QueryService:
				c.PrivateKey = service.TLSOptions.PrivateKeyFile
				c.Certificate = service.TLSOptions.CertificateFile
			default:
				return fmt.Errorf("service not found: %s", curlService)
			}

			if dryRun {
				log.Print(c.String())
			} else {
				if err := c.Run(os.Stdout, os.Stderr); err != nil {
					return fmt.Errorf("failed to execute curl: %w", err)
				}
			}
			return nil
		},
	}
	cmd.Flags().BoolVarP(&dryRun, "dry-run", "n", false, "Print the curl command that would be executed")
	cmd.Flags().StringVarP(&curlService, "service", "s", "query", "Which service to query. Must be \"deploy\", \"document\" or \"query\"")
	return cmd
}

func addCloudAuth0Authentication(system vespa.System, cfg *Config, c *curl.Command) error {
	a, err := auth0.GetAuth0(cfg.authConfigPath(), system.Name, system.URL)
	if err != nil {
		return err
	}

	authSystem, err := a.PrepareSystem(auth0.ContextWithCancel())
	if err != nil {
		return err
	}
	c.Header("Authorization", "Bearer "+authSystem.AccessToken)
	return nil
}

func joinURL(baseURL, path string) string {
	baseURL = strings.TrimSuffix(baseURL, "/")
	path = strings.TrimPrefix(path, "/")
	return baseURL + "/" + path
}
