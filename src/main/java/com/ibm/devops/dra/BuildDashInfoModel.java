/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;

public class BuildDashInfoModel {
    private String build_id;
    private String job_url;
    private String href;
    private String build_status;
    private String timestamp;
    private String built_at;
    private Repo details;
    private Long duration;
    private String repo_url;
    private String branch;
    private String commit;
    private String pull_request_number;
    private String build_engine;

    public BuildDashInfoModel(String build_id, String job_url, String status, String timestamp, Long duration, Repo repository, String RepoBranch, String RepoCommit, String RepoURL, String PRNum, String BuildEngine) {
        this.build_id = build_id;
        this.href = job_url;
        this.built_at = timestamp;
        this.build_status = status;     
        this.duration = duration;
        this.repo_url = RepoURL;
        this.branch = RepoBranch;
        this.commit = RepoCommit;
        this.pull_request_number = PRNum;
        this.build_engine = BuildEngine;
        this.details = repository;
    }

    public String getBuild_id() {
        return build_id;
    }
    
    public Long getDuration() {
    	return duration;
    }

    public String getJob_url() {
        return job_url;
    }

    public String getStatus() {
        return build_status;
    }

    public String getTimestamp() {
        return timestamp;
    }
    
    public String getHref() {
        return href;
    }
    
    public String getBuiltAt() {
        return built_at;
    }

    public Repo getDetails() {
        return details;
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

    public static class Repo {
        private String repository_url;
        private String branch;
        private String commit_id;

        public Repo(String repository_url, String branch, String commit_id) {
            this.repository_url = repository_url;
            this.branch = branch;
            this.commit_id = commit_id;
        }

        public String getRepository_url() {
            return repository_url;
        }

        public String getBranch() {
            return branch;
        }

        public String getCommit_id() {
            return commit_id;
        }
    }


}
