// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

const secretStoreDevAlias = "SANDBOX"

type vaultAccessRule struct {
	Application string   `json:"application"`
	Contexts    []string `json:"contexts"`
	ID          int      `json:"id"`
}

type vaultRulesResponse struct {
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
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode/100 != 2 {
		return "", fmt.Errorf("CSRF endpoint returned %d: %s", resp.StatusCode, body)
	}
	var result struct {
		Token string `json:"token"`
	}
	if err := json.Unmarshal(body, &result); err != nil {
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
	if resp.StatusCode/100 != 2 {
		return fmt.Errorf("could not get vault access rules for %q: server returned %d: %s", vaultName, resp.StatusCode, getRawBody)
	}
	// Parse GET response as a generic map to preserve all fields for the PUT body
	var getRawResp map[string]json.RawMessage
	if err := json.Unmarshal(getRawBody, &getRawResp); err != nil {
		return fmt.Errorf("could not parse vault access rules for %q: %w", vaultName, err)
	}
	var existingRules []vaultAccessRule
	if rulesRaw, ok := getRawResp["rules"]; ok {
		if err := json.Unmarshal(rulesRaw, &existingRules); err != nil {
			return fmt.Errorf("could not parse vault access rules for %q: %w", vaultName, err)
		}
	}

	// Check if access rule already exists for this application with the dev alias
	for _, rule := range existingRules {
		if rule.Application == appID {
			for _, ctx := range rule.Contexts {
				if ctx == secretStoreDevAlias {
					return nil
				}
			}
		}
	}

	// Build new rule and merge into the full GET response body (preserving extra fields)
	newRule := vaultAccessRule{
		Application: appID,
		Contexts:    []string{secretStoreDevAlias},
		ID:          len(existingRules),
	}
	updatedRules := append(existingRules, newRule)
	updatedRulesJSON, err := json.Marshal(updatedRules)
	if err != nil {
		return err
	}
	getRawResp["rules"] = updatedRulesJSON
	body, err := json.Marshal(getRawResp)
	if err != nil {
		return err
	}
	csrfToken, err := t.csrfToken()
	if err != nil {
		return fmt.Errorf("could not fetch CSRF token: %w", err)
	}

	// PUT updated rules
	putReq, err := http.NewRequest("PUT", vaultURL, bytes.NewReader(body))
	if err != nil {
		return err
	}
	putReq.Header.Set("Content-Type", "application/json")
	if csrfToken != "" {
		putReq.Header.Set("vespa-csrf-token", csrfToken)
	}
	putResp, err := deployService.Do(putReq, 10*time.Second)
	if err != nil {
		return fmt.Errorf("could not set vault access rule for %q: %w", vaultName, err)
	}
	defer putResp.Body.Close()
	putRawBody, _ := io.ReadAll(putResp.Body)
	if putResp.StatusCode/100 != 2 {
		return fmt.Errorf("could not set vault access rule for %q: server returned %d: %s", vaultName, putResp.StatusCode, putRawBody)
	}

	// TODO(17.03.2026) BrageHK: This is temporary. It is not possible right now to use GET to fetch the new rules and check if they
	// are actually set. This is because the GET returns the only the old rules unless the application is actually deployed.
	var putResponseBody struct {
		Message string `json:"message"`
	}
	if err := json.Unmarshal(putRawBody, &putResponseBody); err == nil &&
		strings.Contains(putResponseBody.Message, "Set access rules for tenant") {
		return nil
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
	if !ok || len(vaultNames) == 0 || ct.deploymentOptions.Deployment.Zone.Environment != "dev" {
		return nil
	}
	return ct.ensureVaultAccessForDev(vaultNames)
}
