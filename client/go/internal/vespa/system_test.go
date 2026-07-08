// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"testing"
)

func TestSystemURLMethods(t *testing.T) {
	deployment := Deployment{
		System: PublicSystem,
		Application: ApplicationID{
			Tenant:      "my-tenant",
			Application: "my-app",
			Instance:    "my-instance",
		},
		Zone: ZoneID{
			Environment: "dev",
			Region:      "us-north-1",
		},
	}

	t.Run("LogsURL", func(t *testing.T) {
		u := PublicSystem.LogsURL(deployment)
		expected := "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/my-tenant/application/my-app/instance/my-instance/environment/dev/region/us-north-1/logs"
		if u.String() != expected {
			t.Errorf("LogsURL() = %q, want %q", u.String(), expected)
		}
	})

	t.Run("PrivateServicesURL", func(t *testing.T) {
		u := PublicSystem.PrivateServicesURL(deployment)
		expected := "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/my-tenant/application/my-app/instance/my-instance/environment/dev/region/us-north-1/private-services"
		if u.String() != expected {
			t.Errorf("PrivateServicesURL() = %q, want %q", u.String(), expected)
		}
	})

	t.Run("JobPackageURL", func(t *testing.T) {
		u := PublicSystem.JobPackageURL(deployment)
		expected := "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/my-tenant/application/my-app/instance/my-instance/job/dev-us-north-1/package"
		if u.String() != expected {
			t.Errorf("JobPackageURL() = %q, want %q", u.String(), expected)
		}
	})

	t.Run("ApplicationPackageURL", func(t *testing.T) {
		u := PublicSystem.ApplicationPackageURL(deployment.Application)
		expected := "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/my-tenant/application/my-app/package"
		if u.String() != expected {
			t.Errorf("ApplicationPackageURL() = %q, want %q", u.String(), expected)
		}
	})

	t.Run("TenantURL", func(t *testing.T) {
		u := PublicSystem.TenantURL("my-tenant")
		expected := "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/my-tenant"
		if u.String() != expected {
			t.Errorf("TenantURL() = %q, want %q", u.String(), expected)
		}
	})

	t.Run("ApplicationURL", func(t *testing.T) {
		u := PublicSystem.ApplicationURL(deployment.Application)
		expected := "https://api-ctl.vespa-cloud.com:4443/application/v4/tenant/my-tenant/application/my-app"
		if u.String() != expected {
			t.Errorf("ApplicationURL() = %q, want %q", u.String(), expected)
		}
	})
}

func TestSystemURLMethodsPanic(t *testing.T) {
	// Create a system with an invalid base URL that will cause url.Parse to fail
	invalidSystem := System{
		Name: "invalid",
		URL:  "ht tp://invalid url with spaces",
	}

	deployment := Deployment{
		System: invalidSystem,
		Application: ApplicationID{
			Tenant:      "my-tenant",
			Application: "my-app",
			Instance:    "my-instance",
		},
		Zone: ZoneID{
			Environment: "dev",
			Region:      "us-north-1",
		},
	}

	// Test that apiURL() panics on invalid URL - all URL methods use the same underlying function
	defer func() {
		if r := recover(); r == nil {
			t.Errorf("URL method did not panic with invalid base URL")
		}
	}()
	invalidSystem.LogsURL(deployment)
}
