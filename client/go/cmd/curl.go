// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth0"
	"github.com/vespa-engine/vespa/client/go/curl"
)

var curlDryRun bool
var curlService string

func init() {
	rootCmd.AddCommand(curlCmd)
	curlCmd.Flags().BoolVarP(&curlDryRun, "dry-run", "n", false, "Print the curl command that would be executed")
	curlCmd.Flags().StringVarP(&curlService, "service", "s", "query", "Which service to query. Must be \"deploy\", \"document\" or \"query\"")
}

var curlCmd = &cobra.Command{
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
		cfg, err := LoadConfig()
		if err != nil {
			return err
		}
		app, err := getApplication()
		if err != nil {
			return err
		}
		service, err := getService(curlService, 0, "")
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
		case "deploy":
			t, err := getTarget()
			if err != nil {
				return err
			}
			if t.Type() == "cloud" {
				if err := addCloudAuth0Authentication(cfg, c); err != nil {
					return err
				}
			}
		case "document", "query":
			privateKeyFile, err := cfg.PrivateKeyPath(app)
			if err != nil {
				return err
			}
			certificateFile, err := cfg.CertificatePath(app)
			if err != nil {
				return err
			}
			c.PrivateKey = privateKeyFile
			c.Certificate = certificateFile
		default:
			return fmt.Errorf("service not found: %s", curlService)
		}

		if curlDryRun {
			log.Print(c.String())
		} else {
			if err := c.Run(os.Stdout, os.Stderr); err != nil {
				return fmt.Errorf("failed to execute curl: %w", err)
			}
		}
		return nil
	},
}

func addCloudAuth0Authentication(cfg *Config, c *curl.Command) error {
	a, err := auth0.GetAuth0(cfg.AuthConfigPath(), getSystemName(), getApiURL())
	if err != nil {
		return err
	}

	system, err := a.PrepareSystem(auth0.ContextWithCancel())
	if err != nil {
		return err
	}
	c.Header("Authorization", "Bearer "+system.AccessToken)
	return nil
}

func joinURL(baseURL, path string) string {
	baseURL = strings.TrimSuffix(baseURL, "/")
	path = strings.TrimPrefix(path, "/")
	return baseURL + "/" + path
}
