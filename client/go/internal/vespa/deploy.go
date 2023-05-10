// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy API
// Author: bratseth

package vespa

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/version"
)

var DefaultApplication = ApplicationID{Tenant: "default", Application: "application", Instance: "default"}

type ApplicationID struct {
	Tenant      string
	Application string
	Instance    string
}

type ZoneID struct {
	Environment string
	Region      string
}

type Deployment struct {
	System      System
	Application ApplicationID
	Zone        ZoneID
}

type DeploymentOptions struct {
	Target             Target
	ApplicationPackage ApplicationPackage
	Timeout            time.Duration
	Version            version.Version
}

type LogLinePrepareResponse struct {
	Time    int64
	Level   string
	Message string
}

type PrepareResult struct {
	// Session or Run ID
	ID       int64
	LogLines []LogLinePrepareResponse
}

func (a ApplicationID) String() string {
	return fmt.Sprintf("%s.%s.%s", a.Tenant, a.Application, a.Instance)
}

func (a ApplicationID) SerializedForm() string {
	return fmt.Sprintf("%s:%s:%s", a.Tenant, a.Application, a.Instance)
}

func (z ZoneID) String() string {
	return fmt.Sprintf("%s.%s", z.Environment, z.Region)
}

func (d Deployment) String() string {
	return fmt.Sprintf("deployment of %s in %s", d.Application, d.Zone)
}

func (d DeploymentOptions) String() string {
	return fmt.Sprintf("%s to %s", d.Target.Deployment(), d.Target.Type())
}

func (d *DeploymentOptions) url(path string) (*url.URL, error) {
	service, err := d.Target.Service(DeployService, 0, 0, "")
	if err != nil {
		return nil, err
	}
	return url.Parse(service.BaseURL + path)
}

func ApplicationFromString(s string) (ApplicationID, error) {
	parts := strings.Split(s, ".")
	if len(parts) < 2 || len(parts) > 3 {
		return ApplicationID{}, fmt.Errorf("invalid application: %q", s)
	}
	instance := "default"
	if len(parts) == 3 {
		instance = parts[2]
	}
	return ApplicationID{Tenant: parts[0], Application: parts[1], Instance: instance}, nil
}

func ZoneFromString(s string) (ZoneID, error) {
	parts := strings.Split(s, ".")
	if len(parts) != 2 {
		return ZoneID{}, fmt.Errorf("invalid zone: %q", s)
	}
	return ZoneID{Environment: parts[0], Region: parts[1]}, nil
}

// Prepare deployment and return the session ID
func Prepare(deployment DeploymentOptions) (PrepareResult, error) {
	if deployment.Target.IsCloud() {
		return PrepareResult{}, fmt.Errorf("prepare is not supported with %s target", deployment.Target.Type())
	}
	sessionURL, err := deployment.url("/application/v2/tenant/default/session")
	if err != nil {
		return PrepareResult{}, err
	}
	result, err := uploadApplicationPackage(sessionURL, deployment)
	if err != nil {
		return PrepareResult{}, err
	}
	prepareURL, err := deployment.url(fmt.Sprintf("/application/v2/tenant/default/session/%d/prepared", result.ID))
	if err != nil {
		return PrepareResult{}, err
	}
	req, err := http.NewRequest("PUT", prepareURL.String(), nil)
	if err != nil {
		return PrepareResult{}, err
	}
	serviceDescription := "Deploy service"
	response, err := deployServiceDo(req, time.Second*30, deployment)
	if err != nil {
		return PrepareResult{}, err
	}
	defer response.Body.Close()
	if err := checkResponse(req, response, serviceDescription); err != nil {
		return PrepareResult{}, err
	}
	var jsonResponse struct {
		SessionID string                   `json:"session-id"`
		Log       []LogLinePrepareResponse `json:"log"`
	}
	jsonDec := json.NewDecoder(response.Body)
	if err := jsonDec.Decode(&jsonResponse); err != nil {
		return PrepareResult{}, err
	}
	var id int64
	id, err = strconv.ParseInt(jsonResponse.SessionID, 10, 64)
	if err != nil {
		return PrepareResult{}, err
	}
	return PrepareResult{
		ID:       id,
		LogLines: jsonResponse.Log,
	}, err
}

// Activate deployment with sessionID from a past prepare
func Activate(sessionID int64, deployment DeploymentOptions) error {
	if deployment.Target.IsCloud() {
		return fmt.Errorf("activate is not supported with %s target", deployment.Target.Type())
	}
	u, err := deployment.url(fmt.Sprintf("/application/v2/tenant/default/session/%d/active", sessionID))
	if err != nil {
		return err
	}
	req, err := http.NewRequest("PUT", u.String(), nil)
	if err != nil {
		return err
	}
	serviceDescription := "Deploy service"
	response, err := deployServiceDo(req, time.Second*30, deployment)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	return checkResponse(req, response, serviceDescription)
}

func Deploy(opts DeploymentOptions) (PrepareResult, error) {
	path := "/application/v2/tenant/default/prepareandactivate"
	if opts.Target.IsCloud() {
		if err := checkDeploymentOpts(opts); err != nil {
			return PrepareResult{}, err
		}
		if opts.Target.Deployment().Zone.Environment == "" || opts.Target.Deployment().Zone.Region == "" {
			return PrepareResult{}, fmt.Errorf("%s: missing zone", opts)
		}
		path = fmt.Sprintf("/application/v4/tenant/%s/application/%s/instance/%s/deploy/%s-%s",
			opts.Target.Deployment().Application.Tenant,
			opts.Target.Deployment().Application.Application,
			opts.Target.Deployment().Application.Instance,
			opts.Target.Deployment().Zone.Environment,
			opts.Target.Deployment().Zone.Region)
	}
	u, err := opts.url(path)
	if err != nil {
		return PrepareResult{}, err
	}
	return uploadApplicationPackage(u, opts)
}

func copyToPart(dst *multipart.Writer, src io.Reader, fieldname, filename string) error {
	var part io.Writer
	var err error
	if filename == "" {
		part, err = dst.CreateFormField(fieldname)
	} else {
		part, err = dst.CreateFormFile(fieldname, filename)
	}
	if err != nil {
		return err
	}
	if _, err := io.Copy(part, src); err != nil {
		return err
	}
	return nil
}

func Submit(opts DeploymentOptions) error {
	if !opts.Target.IsCloud() {
		return fmt.Errorf("%s: deploy is unsupported by %s target", opts, opts.Target.Type())
	}
	if err := checkDeploymentOpts(opts); err != nil {
		return err
	}
	path := fmt.Sprintf("/application/v4/tenant/%s/application/%s/submit", opts.Target.Deployment().Application.Tenant, opts.Target.Deployment().Application.Application)
	u, err := opts.url(path)
	if err != nil {
		return err
	}
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	if err := copyToPart(writer, strings.NewReader("{}"), "submitOptions", ""); err != nil {
		return err
	}
	applicationZip, err := opts.ApplicationPackage.zipReader(false)
	if err != nil {
		return err
	}
	if err := copyToPart(writer, applicationZip, "applicationZip", "application.zip"); err != nil {
		return err
	}
	if opts.ApplicationPackage.HasTests() {
		testApplicationZip, err := opts.ApplicationPackage.zipReader(true)
		if err != nil {
			return err
		}
		if err := copyToPart(writer, testApplicationZip, "applicationTestZip", "application-test.zip"); err != nil {
			return err
		}
	}
	if err := writer.Close(); err != nil {
		return err
	}
	request := &http.Request{
		URL:    u,
		Method: "POST",
		Body:   io.NopCloser(&body),
		Header: make(http.Header),
	}
	request.Header.Set("Content-Type", writer.FormDataContentType())
	serviceDescription := "Deploy service"
	response, err := deployServiceDo(request, time.Minute*10, opts)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	return checkResponse(request, response, serviceDescription)
}

func deployServiceDo(request *http.Request, timeout time.Duration, opts DeploymentOptions) (*http.Response, error) {
	s, err := opts.Target.Service(DeployService, 0, 0, "")
	if err != nil {
		return nil, err
	}
	return s.Do(request, timeout)
}

func checkDeploymentOpts(opts DeploymentOptions) error {
	if opts.Target.Type() == TargetCloud && !opts.ApplicationPackage.HasCertificate() {
		return fmt.Errorf("%s: missing certificate in package", opts)
	}
	if !opts.Target.IsCloud() && !opts.Version.IsZero() {
		return fmt.Errorf("%s: custom runtime version is not supported by %s target", opts, opts.Target.Type())
	}
	return nil
}

func newDeploymentRequest(url *url.URL, opts DeploymentOptions) (*http.Request, error) {
	zipReader, err := opts.ApplicationPackage.zipReader(false)
	if err != nil {
		return nil, err
	}
	var body io.Reader
	header := http.Header{}
	if opts.Target.IsCloud() {
		var buf bytes.Buffer
		form := multipart.NewWriter(&buf)
		formFile, err := form.CreateFormFile("applicationZip", filepath.Base(opts.ApplicationPackage.Path))
		if err != nil {
			return nil, err
		}
		if _, err := io.Copy(formFile, zipReader); err != nil {
			return nil, err
		}
		if !opts.Version.IsZero() {
			deployOptions := fmt.Sprintf(`{"vespaVersion":"%s"}`, opts.Version.String())
			if err := form.WriteField("deployOptions", deployOptions); err != nil {
				return nil, err
			}
		}
		if err := form.Close(); err != nil {
			return nil, err
		}
		header.Set("Content-Type", form.FormDataContentType())
		body = &buf
	} else {
		header.Set("Content-Type", "application/zip")
		body = zipReader
	}
	return &http.Request{
		URL:    url,
		Method: "POST",
		Header: header,
		Body:   io.NopCloser(body),
	}, nil
}

func uploadApplicationPackage(url *url.URL, opts DeploymentOptions) (PrepareResult, error) {
	request, err := newDeploymentRequest(url, opts)
	if err != nil {
		return PrepareResult{}, err
	}
	service, err := opts.Target.Service(DeployService, opts.Timeout, 0, "")
	if err != nil {
		return PrepareResult{}, err
	}
	response, err := service.Do(request, time.Minute*10)
	if err != nil {
		return PrepareResult{}, err
	}
	defer response.Body.Close()

	var jsonResponse struct {
		SessionID string `json:"session-id"` // Config server
		RunID     int64  `json:"run"`        // Controller

		Log []LogLinePrepareResponse `json:"log"`
	}
	jsonResponse.SessionID = "0" // Set a default session ID for responses that don't contain int (e.g. cloud deployment)
	if err := checkResponse(request, response, service.Description()); err != nil {
		return PrepareResult{}, err
	}
	jsonDec := json.NewDecoder(response.Body)
	jsonDec.Decode(&jsonResponse) // Ignore error in case this is a non-JSON response
	id := jsonResponse.RunID
	if id == 0 {
		id, err = strconv.ParseInt(jsonResponse.SessionID, 10, 64)
		if err != nil {
			return PrepareResult{}, err
		}
	}
	return PrepareResult{
		ID:       id,
		LogLines: jsonResponse.Log,
	}, err
}

func checkResponse(req *http.Request, response *http.Response, serviceDescription string) error {
	if response.StatusCode/100 == 4 {
		return fmt.Errorf("invalid application package (%s)\n%s", response.Status, extractError(response.Body))
	} else if response.StatusCode != 200 {
		return fmt.Errorf("error from %s at %s (%s):\n%s", strings.ToLower(serviceDescription), req.URL.Host, response.Status, util.ReaderToJSON(response.Body))
	}
	return nil
}

// Returns the error message in the given JSON, or the entire content if it could not be extracted
func extractError(reader io.Reader) string {
	responseData, _ := io.ReadAll(reader)
	var response map[string]interface{}
	json.Unmarshal(responseData, &response)
	if response["error-code"] == "INVALID_APPLICATION_PACKAGE" {
		return strings.ReplaceAll(response["message"].(string), ": ", ":\n")
	} else {
		var prettyJSON bytes.Buffer
		parseError := json.Indent(&prettyJSON, responseData, "", "    ")
		if parseError != nil { // Not JSON: Print plainly
			return string(responseData)
		}
		return prettyJSON.String()
	}
}
