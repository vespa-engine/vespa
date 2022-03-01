// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy API
// Author: bratseth

package vespa

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"mime/multipart"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/util"
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
}

func (a ApplicationID) String() string {
	return fmt.Sprintf("%s.%s.%s", a.Tenant, a.Application, a.Instance)
}

func (a ApplicationID) SerializedForm() string {
	return fmt.Sprintf("%s:%s:%s", a.Tenant, a.Application, a.Instance)
}

func (d Deployment) String() string {
	return fmt.Sprintf("deployment of %s in %s", d.Application, d.Zone)
}

func (d DeploymentOptions) String() string {
	return fmt.Sprintf("%s to %s", d.Target.Deployment(), d.Target.Type())
}

// IsCloud returns whether this is a deployment to Vespa Cloud or hosted Vespa
func (d *DeploymentOptions) IsCloud() bool {
	return d.Target.Type() == TargetCloud || d.Target.Type() == TargetHosted
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
	if len(parts) != 3 {
		return ApplicationID{}, fmt.Errorf("invalid application: %q", s)
	}
	return ApplicationID{Tenant: parts[0], Application: parts[1], Instance: parts[2]}, nil
}

func ZoneFromString(s string) (ZoneID, error) {
	parts := strings.Split(s, ".")
	if len(parts) != 2 {
		return ZoneID{}, fmt.Errorf("invalid zone: %q", s)
	}
	return ZoneID{Environment: parts[0], Region: parts[1]}, nil
}

// Prepare deployment and return the session ID
func Prepare(deployment DeploymentOptions) (int64, error) {
	if deployment.IsCloud() {
		return 0, fmt.Errorf("prepare is not supported with %s target", deployment.Target.Type())
	}
	sessionURL, err := deployment.url("/application/v2/tenant/default/session")
	if err != nil {
		return 0, err
	}
	sessionID, err := uploadApplicationPackage(sessionURL, deployment)
	if err != nil {
		return 0, err
	}
	prepareURL, err := deployment.url(fmt.Sprintf("/application/v2/tenant/default/session/%d/prepared", sessionID))
	if err != nil {
		return 0, err
	}
	req, err := http.NewRequest("PUT", prepareURL.String(), nil)
	if err != nil {
		return 0, err
	}
	serviceDescription := "Deploy service"
	response, err := util.HttpDo(req, time.Second*30, serviceDescription)
	if err != nil {
		return 0, err
	}
	defer response.Body.Close()
	if err := checkResponse(req, response, serviceDescription); err != nil {
		return 0, err
	}
	return sessionID, nil
}

// Activate deployment with sessionID from a past prepare
func Activate(sessionID int64, deployment DeploymentOptions) error {
	if deployment.IsCloud() {
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
	response, err := util.HttpDo(req, time.Second*30, serviceDescription)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	return checkResponse(req, response, serviceDescription)
}

func Deploy(opts DeploymentOptions) (int64, error) {
	path := "/application/v2/tenant/default/prepareandactivate"
	if opts.IsCloud() {
		if err := checkDeploymentOpts(opts); err != nil {
			return 0, err
		}
		if opts.Target.Deployment().Zone.Environment == "" || opts.Target.Deployment().Zone.Region == "" {
			return 0, fmt.Errorf("%s: missing zone", opts)
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
		return 0, err
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
	if !opts.IsCloud() {
		return fmt.Errorf("%s: submit is unsupported by %s target", opts, opts.Target.Type())
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
	testApplicationZip, err := opts.ApplicationPackage.zipReader(true)
	if err != nil {
		return err
	}
	if err := copyToPart(writer, testApplicationZip, "applicationTestZip", "application-test.zip"); err != nil {
		return err
	}
	if err := writer.Close(); err != nil {
		return err
	}
	request := &http.Request{
		URL:    u,
		Method: "POST",
		Body:   ioutil.NopCloser(&body),
		Header: make(http.Header),
	}
	request.Header.Set("Content-Type", writer.FormDataContentType())
	serviceDescription := "Submit service"
	sigKeyId := opts.Target.Deployment().Application.SerializedForm()
	if err := opts.Target.SignRequest(request, sigKeyId); err != nil {
		return err
	}
	response, err := util.HttpDo(request, time.Minute*10, sigKeyId)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	return checkResponse(request, response, serviceDescription)
}

func checkDeploymentOpts(opts DeploymentOptions) error {
	if opts.Target.Type() == TargetCloud && !opts.ApplicationPackage.HasCertificate() {
		return fmt.Errorf("%s: missing certificate in package", opts)
	}
	return nil
}

func uploadApplicationPackage(url *url.URL, opts DeploymentOptions) (int64, error) {
	zipReader, err := opts.ApplicationPackage.zipReader(false)
	if err != nil {
		return 0, err
	}
	header := http.Header{}
	header.Add("Content-Type", "application/zip")
	request := &http.Request{
		URL:    url,
		Method: "POST",
		Header: header,
		Body:   ioutil.NopCloser(zipReader),
	}
	service, err := opts.Target.Service(DeployService, opts.Timeout, 0, "")
	if err != nil {
		return 0, err
	}

	keyID := opts.Target.Deployment().Application.SerializedForm()
	if err := opts.Target.SignRequest(request, keyID); err != nil {
		return 0, err
	}
	response, err := service.Do(request, time.Minute*10)
	if err != nil {
		return 0, err
	}
	defer response.Body.Close()

	var jsonResponse struct {
		SessionID string `json:"session-id"` // Config server
		RunID     int64  `json:"run"`        // Controller
	}
	jsonResponse.SessionID = "0" // Set a default session ID for responses that don't contain int (e.g. cloud deployment)
	if err := checkResponse(request, response, service.Description()); err != nil {
		return 0, err
	}
	jsonDec := json.NewDecoder(response.Body)
	jsonDec.Decode(&jsonResponse) // Ignore error in case this is a non-JSON response
	if jsonResponse.RunID > 0 {
		return jsonResponse.RunID, nil
	}
	return strconv.ParseInt(jsonResponse.SessionID, 10, 64)
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
	responseData, _ := ioutil.ReadAll(reader)
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
