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

import com.ibm.devops.dra.BuildDashInfoModel.Repo;

import javax.annotation.Nonnull;

public class PublishBuildStep extends AbstractStepImpl {
    // optional form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainId;
    private String hostName;
    private String serviceName;

    // required parameters to support pipeline script
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;
    private Long duration;
    private String repo_url;
    private String branch;
    private String commit;
    private String pull_request_number;
    private String build_engine;
    private Repo details;
    // custom build number, optional
    private String buildNumber;

    @DataBoundConstructor
    public PublishBuildStep(String result, String gitRepo, String gitBranch, String gitCommit,Long duration) {
        this.gitRepo = gitRepo;
        this.gitBranch = gitBranch;
        this.gitCommit = gitCommit;
        this.result = result;
        this.duration = duration;
    }

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
    
   
    
    @DataBoundSetter
    public void setserviceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    @DataBoundSetter
    public void setHostName(String hostName) {
        this.hostName = hostName;
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
    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getApplicationName() {
        return applicationName;
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public String getServiceName() {
        return serviceName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getGitRepo() {
        return gitRepo;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public String getGitCommit() {
        return gitCommit;
    }
    
    public Long getDuration() {
        return duration;
    }
       
    public String getRepo_url() {
        return repo_url;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public String getCommit() {
        return commit;
    }
    
    public String getPull_request_number() {
    	return pull_request_number;
    }
    
    public String getBuild_engine() {
        return build_engine;
    }

    public String getToolchainId() {
        return toolchainId;
    }

    public String getResult() {
        return result;
    }

    public String getBuildNumber() {
        return buildNumber;
    }
    
    public Repo getDetails() {
        return details;
    }


    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() { super(PublishBuildStepExecution.class); }

        @Override
        public String getFunctionName() {
            return "publishBuildRecord";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish build record to IBM Cloud DevOps";
        }
    }
}
