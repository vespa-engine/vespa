// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"log"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/curl"
)

var curlDryRun bool

func init() {
	rootCmd.AddCommand(curlCmd)
	curlCmd.Flags().BoolVarP(&curlDryRun, "dry-run", "n", false, "Print the curl command that would be executed")
}

var curlCmd = &cobra.Command{
	Use:   "curl [curl-options] path",
	Short: "Query Vespa using curl",
	Long: `Query Vespa using curl.

Execute curl with the appropriate URL, certificate and private key for your application.`,
	Example: `$ vespa curl /search/?yql=query
$ vespa curl -- -v --data-urlencode "yql=select * from sources * where title contains 'foo';" /search/
$ vespa curl -t local -- -v /search/?yql=query
`,
	DisableAutoGenTag: true,
	Args:              cobra.MinimumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}
		app := getApplication()
		privateKeyFile, err := cfg.PrivateKeyPath(app)
		if err != nil {
			fatalErr(err)
			return
		}
		certificateFile, err := cfg.CertificatePath(app)
		if err != nil {
			fatalErr(err)
			return
		}
		service := getService("query", 0)
		url := joinURL(service.BaseURL, args[len(args)-1])
		rawArgs := args[:len(args)-1]
		c, err := curl.RawArgs(url, rawArgs...)
		if err != nil {
			fatalErr(err)
			return
		}
		c.PrivateKey = privateKeyFile
		c.Certificate = certificateFile

		if curlDryRun {
			log.Print(c.String())
		} else {
			if err := c.Run(os.Stdout, os.Stderr); err != nil {
				fatalErr(err, "Failed to run curl")
				return
			}
		}
	},
}

func joinURL(baseURL, path string) string {
	baseURL = strings.TrimSuffix(baseURL, "/")
	path = strings.TrimPrefix(path, "/")
	return baseURL + "/" + path
}
