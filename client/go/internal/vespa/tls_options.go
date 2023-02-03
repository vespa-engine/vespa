// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package vespa

import (
	"bytes"
	"encoding/json"
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
)

type VespaTlsConfig struct {
	DisableHostnameValidation bool `json:"disable-hostname-validation"`
	Files                     struct {
		PrivateKey     string `json:"private-key"`
		CaCertificates string `json:"ca-certificates"`
		Certificates   string `json:"certificates"`
	} `json:"files"`
}

func LoadTlsConfig() (*VespaTlsConfig, error) {
	fn := os.Getenv(envvars.VESPA_TLS_CONFIG_FILE)
	if fn == "" {
		return nil, nil
	}
	contents, err := os.ReadFile(fn)
	if err != nil {
		return nil, err
	}
	codec := json.NewDecoder(bytes.NewReader(contents))
	var parsedJson VespaTlsConfig
	err = codec.Decode(&parsedJson)
	if err != nil {
		return nil, err
	}
	return &parsedJson, nil
}

func ExportSecurityEnvToSh() {
	LoadDefaultEnv()
	cfg, _ := LoadTlsConfig()
	helper := newShellEnvExporter()
	if cfg == nil {
		helper.unsetVar(envvars.VESPA_TLS_ENABLED)
	} else {
		if fn := cfg.Files.PrivateKey; fn != "" {
			helper.overrideVar(envvars.VESPA_TLS_PRIVATE_KEY, fn)
		}
		if fn := cfg.Files.CaCertificates; fn != "" {
			helper.overrideVar(envvars.VESPA_TLS_CA_CERT, fn)
		}
		if fn := cfg.Files.Certificates; fn != "" {
			helper.overrideVar(envvars.VESPA_TLS_CERT, fn)
		}
		if cfg.DisableHostnameValidation {
			helper.overrideVar(envvars.VESPA_TLS_HOSTNAME_VALIDATION_DISABLED, "1")
		} else {
			helper.unsetVar(envvars.VESPA_TLS_HOSTNAME_VALIDATION_DISABLED)
		}
		if os.Getenv(envvars.VESPA_TLS_INSECURE_MIXED_MODE) != "plaintext_client_mixed_server" {
			helper.overrideVar(envvars.VESPA_TLS_ENABLED, "1")
		}
	}
	helper.dump()
}
