/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra.steps;

import com.ibm.devops.dra.PublishDashTest;
import com.ibm.devops.dra.PublishTest;
import com.ibm.devops.dra.Util;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.PrintStream;

public class PublishTestStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;
    @Inject
    private transient PublishTestStep step;

    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient FilePath ws;
    @StepContextParameter
    private transient Launcher launcher;
    @StepContextParameter
    private transient Run build;
    @StepContextParameter
    private transient EnvVars envVars;

    @Override
    protected Void run() throws Exception {

        PrintStream printStream = listener.getLogger();

        String orgName = Util.isNullOrEmpty(step.getOrgName()) ? envVars.get("IBM_CLOUD_DEVOPS_ORG") : step.getOrgName();
        String applicationName =  Util.isNullOrEmpty(step.getApplicationName()) ? envVars.get("IBM_CLOUD_DEVOPS_APP_NAME") : step.getApplicationName();
        String toolchainName = Util.isNullOrEmpty(step.getToolchainId()) ? envVars.get("IBM_CLOUD_DEVOPS_TOOLCHAIN_ID") : step.getToolchainId();
        String username = envVars.get("IBM_CLOUD_DEVOPS_CREDS_USR");
        String password = envVars.get("IBM_CLOUD_DEVOPS_CREDS_PSW");
        String hostName = Util.isNullOrEmpty(step.getHostName()) ? envVars.get("IBM_DASH_HOSTNAME") : step.getHostName();
        String serviceName = Util.isNullOrEmpty(step.getServiceName()) ? envVars.get("IBM_DASH_SERVICENAME") : step.getServiceName();


        //check all the required env vars
        if (!Util.allNotNullOrEmpty(orgName, applicationName,toolchainName, username, password)) {
            printStream.println("[IBM Cloud DevOps] Missing environment variables configurations, please specify all required environment variables in the pipeline");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Test Result.");
            return null;
        }

        //check all the required parameters
        String type = step.getType();
        String resultType = step.getResultType();
        String fileLocation = step.getFileLocation();
        // optional build number, if user wants to set their own build number
        String buildNumber = step.getBuildNumber();

        if (!Util.allNotNullOrEmpty(type, fileLocation)) {
            printStream.println("[IBM Cloud DevOps] publishTestResult is missing required parameters, " +
                    "please make sure you specify \"type\", \"fileLocation\"");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Test Result.");
            return null;
        }

        if (type.equals("unittest") || type.equals("code") || type.equals("fvt")) {
            PublishTest publishTest = new PublishTest(
                    type,
                    fileLocation,
                    step.getEnvironment(),
                    orgName,
                    applicationName,
                    toolchainName,
                    username,
                    password);
            if (!Util.isNullOrEmpty(buildNumber)) {
                publishTest.setBuildNumber(buildNumber);
            }
            publishTest.perform(build, ws, launcher, listener);
        } else {
            printStream.println("[IBM Cloud DevOps] the \"type\" in the publishTestResult should be either \"unittest\", \"code\" or \"fvt\"");
        }
        
        if (type.equals("unit") || type.equals("code") || type.equals("fvt")) {
            PublishDashTest publishDashTest = new PublishDashTest(
                    type,
                    resultType,
                    fileLocation,
//                    step.getEnvironment(),
                    orgName,
                    applicationName,
                    toolchainName,
                    username,
                    password,
                    hostName,
                    serviceName);
            if (!Util.isNullOrEmpty(buildNumber)) {
                publishDashTest.setBuildNumber(buildNumber);
            }
            publishDashTest.perform(build, ws, launcher, listener);
        } else {
            printStream.println("[IBM Cloud DevOps] the \"type\" in the publishTestResult should be either \"unittest\", \"code\" or \"fvt\"");
        }

        return null;
    }
}
