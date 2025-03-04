// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"net/http"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
)

func (cli *CLI) selectAuthMethod() (authMethod string) {
	token := cli.Environment[envvars.VESPA_CLI_DATA_PLANE_TOKEN]
	authMethod = "mtls"
	if token != "" {
		cli.printDebug("The VESPA_CLI_DATA_PLANE_TOKEN environment variable is set, using token authentication")
		authMethod = "token"
	}
	return
}

func (cli *CLI) addBearerToken(header *http.Header) error {
	token := cli.Environment[envvars.VESPA_CLI_DATA_PLANE_TOKEN]
	if token != "" {
		if header.Get("Authorization") != "" {
			err := fmt.Errorf("header 'Authorization' cannot be set in combination with VESPA_CLI_DATA_PLANE_TOKEN")
			return errHint(err, "Unset the VESPA_CLI_DATA_PLANE_TOKEN environment variable or remove the Authorization header")
		}
		header.Set("Authorization", fmt.Sprintf("Bearer %s", token))
	}
	return nil
}
