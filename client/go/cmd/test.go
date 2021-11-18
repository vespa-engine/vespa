// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa test command
// Author: jonmv

package cmd

import (
	"encoding/json"
	"fmt"
	"github.com/vespa-engine/vespa/client/go/vespa"
	"log"

	"github.com/spf13/cobra"
)

const (
	endpointsFlag     = "endpoints"
	dataPlaneCertFlag = "data-plane-public-cert"
	dataPlaneKeyFlag  = "data-plane-private-key"
)

var (
	endpointsArg     string
	dataPlaneCertArg string
	dataPlaneKeyArg  string
)

func init() {
	rootCmd.AddCommand(testCmd)
	testCmd.PersistentFlags().StringVar(&endpointsArg, endpointsFlag, "", "The endpoints to use, for each container cluster")
	testCmd.PersistentFlags().StringVar(&dataPlaneCertArg, dataPlaneCertFlag, "", "Override for location of data plane public certificate")
	testCmd.PersistentFlags().StringVar(&dataPlaneKeyArg, dataPlaneKeyFlag, "", "Override for location of data plane private key")
}

var testCmd = &cobra.Command{
	Use:   "test [tests directory or test file]",
	Short: "Run a test suite, or a single test",
	Long: `Run a test suite, or a single test

Runs all JSON test files in the specified directory, or the single JSON
test file specified.

If no directory or file is specified, the working directory is used instead.`,
	Example: `$ vespa test src/test/application/tests/system-test
$ vespa test src/test/application/tests/system-test/feed-and-query.json`,
	Args:              cobra.MaximumNArgs(1),
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}

		app, endpoints := getApplicationOrEndpoints(cfg)
		keyPath, _ := cfg.PrivateKeyPath(app)
		certPath, _ := cfg.CertificatePath(app)
		log.Printf("Key path: %q, Cert path: %q, Endpoints: %q", keyPath, certPath, endpoints)

		exitFunc(3)
	},
}

func getApplicationOrEndpoints(cfg *Config) (vespa.ApplicationID, map[string]string) {
	appString, _ := cfg.Get(applicationFlag)
	endpointsString, _ := cfg.Get(endpointsFlag)
	if (appString == "") == (endpointsString == "") {
		fatalErr(fmt.Errorf("must specify exactly one of application or endpoints"))
	}
	if endpointsString == "" {
		app, err := vespa.ApplicationFromString(appString)
		if err != nil {
			fatalErrHint(err, "Application format is <tenant>.<app>.<instance>")
		}
		return app, nil
	} else {
		var endpoints deploymentResponse
		urlsByCluster := make(map[string]string)
		if err := json.Unmarshal([]byte(endpointsString), &endpoints); err != nil {
			fatalErrHint(err, "Endpoints must be valid JSON")
		}
		if len(endpoints.Endpoints) == 0 {
			fatalErr(fmt.Errorf("endpoints must be non-empty"))
		}
		for _, endpoint := range endpoints.Endpoints {
			urlsByCluster[endpoint.Cluster] = endpoint.URL
		}
		return vespa.ApplicationID{}, urlsByCluster
	}
}

type deploymentEndpoint struct {
	Cluster string `json:"cluster"`
	URL     string `json:"url"`
	Scope   string `json:"scope"`
}

type deploymentResponse struct {
	Endpoints []deploymentEndpoint `json:"endpoints"`
}
