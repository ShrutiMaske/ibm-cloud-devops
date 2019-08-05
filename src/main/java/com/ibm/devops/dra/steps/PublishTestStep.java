/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

public class PublishTestStep extends AbstractStepImpl {
    // optional form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;
    private String environment;
    private String resultType;
    private String hostName;
    private String serviceName;
    
    // required parameters
    private String type;
    private String fileLocation;
    // custom build number, optional
    private String buildNumber;

    @DataBoundConstructor
    public PublishTestStep(String type, String fileLocation) {
        this.type = type;
        this.fileLocation = fileLocation;
    }

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
    
    @DataBoundSetter
    public void setResultType(String resultType) {
        this.resultType = resultType;
    }
    
    @DataBoundSetter
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }
    
    @DataBoundSetter
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @DataBoundSetter
    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setToolchainId(String toolchainId) {
        this.toolchainId = toolchainId;
    }

    @DataBoundSetter
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    @DataBoundSetter
    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public String getServiceName() {
        return serviceName;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getApplicationName() {
        return applicationName;
    }
    
    public String getResultType() {
        return resultType;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getToolchainId() {
        return toolchainId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getType() {
        return type;
    }

    public String getFileLocation() {
        return fileLocation;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() { super(PublishTestStepExecution.class); }

        @Override
        public String getFunctionName() {
            return "publishTestResult";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish test result to IBM Cloud DevOps";
        }
    }
}
