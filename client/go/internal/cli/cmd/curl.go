// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/curl"
)

func newCurlCmd(cli *CLI) *cobra.Command {
	var (
		waitSecs int
		dryRun   bool
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
$ vespa curl -- -v --data-urlencode "yql=select * from music where album contains 'head'" /search/\?hits=5`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			waiter := cli.waiter(time.Duration(waitSecs) * time.Second)
			service, err := waiter.Service(target, cli.config.cluster())
			if err != nil {
				return err
			}
			url := joinURL(service.BaseURL, args[len(args)-1])
			rawArgs := args[:len(args)-1]
			c, err := curl.RawArgs(url, rawArgs...)
			if err != nil {
				return err
			}
			c.CaCertificate = service.TLSOptions.CACertificateFile
			c.PrivateKey = service.TLSOptions.PrivateKeyFile
			c.Certificate = service.TLSOptions.CertificateFile
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
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func joinURL(baseURL, path string) string {
	baseURL = strings.TrimSuffix(baseURL, "/")
	path = strings.TrimPrefix(path, "/")
	return baseURL + "/" + path
}
