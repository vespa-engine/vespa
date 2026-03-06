// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

const SECRET_STORE_DEV_ALIAS = "SANDBOX"

type vaultAccessRule struct {
	Application string   `json:"application"`
	Contexts    []string `json:"contexts"`
	ID          int      `json:"id"`
}

type vaultResponse struct {
	Rules []vaultAccessRule `json:"rules"`
}

func (t *cloudTarget) vaultAccessURL(tenant, vaultName string) string {
	return fmt.Sprintf("%s/tenant-secret/v1/tenant/%s/vault/%s", t.apiOptions.System.URL, tenant, vaultName)
}

func (t *cloudTarget) csrfToken() (string, error) {
	req, err := http.NewRequest("GET", fmt.Sprintf("%s/csrf/v1", t.apiOptions.System.URL), nil)
	if err != nil {
		return "", err
	}
	deployService, err := t.DeployService()
	if err != nil {
		return "", err
	}
	resp, err := deployService.Do(req, 10*time.Second)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	var result struct {
		Token string `json:"token"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", err
	}
	return result.Token, nil
}

func (t *cloudTarget) ensureVaultAccessRule(vaultName string) error {
	deployment := t.deploymentOptions.Deployment
	tenant := deployment.Application.Tenant
	appID := deployment.Application.Application // just the application name; tenant is already in the URL path
	vaultURL := t.vaultAccessURL(tenant, vaultName)

	// GET existing rules
	req, err := http.NewRequest("GET", vaultURL, nil)
	if err != nil {
		return err
	}
	deployService, err := t.DeployService()
	if err != nil {
		return err
	}
	resp, err := deployService.Do(req, 10*time.Second)
	if err != nil {
		return fmt.Errorf("could not get vault access rules for %q: %w", vaultName, err)
	}
	defer resp.Body.Close()
	getRawBody, _ := io.ReadAll(resp.Body)
	var vaultResp vaultResponse
	if err := json.Unmarshal(getRawBody, &vaultResp); err != nil {
		return fmt.Errorf("could not parse vault access rules for %q: %w", vaultName, err)
	}

	// Check if access rule already exists for this application with the dev alias
	for _, rule := range vaultResp.Rules {
		if rule.Application == appID {
			for _, ctx := range rule.Contexts {
				if ctx == SECRET_STORE_DEV_ALIAS {
					return nil
				}
			}
		}
	}

	// Build new rule with no context restriction (grants access to all environments)
	newRule := vaultAccessRule{
		Application: appID,
		Contexts:    []string{SECRET_STORE_DEV_ALIAS},
		ID:          len(vaultResp.Rules),
	}
	updatedRules := vaultResponse{Rules: append(vaultResp.Rules, newRule)}
	body, err := json.Marshal(updatedRules)
	if err != nil {
		return err
	}

	csrfToken, _ := t.csrfToken()

	// PUT updated rules
	putReq, err := http.NewRequest("PUT", vaultURL, bytes.NewReader(body))
	if err != nil {
		return err
	}
	putReq.Header.Set("Content-Type", "application/json")
	if csrfToken != "" {
		putReq.Header.Set("vespa-csrf-token", csrfToken)
	}
	deployService2, err := t.DeployService()
	if err != nil {
		return err
	}
	putResp, err := deployService2.Do(putReq, 10*time.Second)
	if err != nil {
		return fmt.Errorf("could not set vault access rule for %q: %w", vaultName, err)
	}
	defer putResp.Body.Close()
	putRawBody, _ := io.ReadAll(putResp.Body)
	fmt.Printf("[vault DEBUG] PUT %s -> status=%d body=%s\n", vaultURL, putResp.StatusCode, putRawBody)
	var putVaultResp vaultResponse
	if err := json.Unmarshal(putRawBody, &putVaultResp); err != nil {
		return fmt.Errorf("could not parse vault PUT response for %q: %w", vaultName, err)
	}

	// Verify the new rule is present in response
	for _, rule := range vaultResp.Rules {
		if rule.Application == appID {
			for _, ctx := range rule.Contexts {
				if ctx == SECRET_STORE_DEV_ALIAS {
					return nil
				}
			}
		}
	}
	return fmt.Errorf("vault access rule for %q was not confirmed in response", vaultName)
}

func (t *cloudTarget) ensureVaultAccessForDev(vaultNames []string) error {
	for _, name := range vaultNames {
		if err := t.ensureVaultAccessRule(name); err != nil {
			return err
		}
	}
	return nil
}

// EnsureVaultAccessForDev checks and sets vault access rules for dev deployments.
// Returns nil for non-cloud targets or when no vaults are referenced.
// Errors are non-fatal warnings for the caller.
func EnsureVaultAccessForDev(target Target, vaultNames []string) error {
	ct, ok := target.(*cloudTarget)
	if !ok || len(vaultNames) == 0 {
		return nil
	}
	return ct.ensureVaultAccessForDev(vaultNames)
}
