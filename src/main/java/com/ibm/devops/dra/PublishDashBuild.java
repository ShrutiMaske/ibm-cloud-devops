/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.*;
import hudson.*;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;
import org.kohsuke.stapler.*;
import java.lang.Object;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.net.URL;
import java.net.URLEncoder;

public class PublishDashBuild   extends AbstractDevOpsAction implements SimpleBuildStep {
	//extends AbstractDevOpsAction
//    private String userId;
//    private String keyName;
    private String hostName;
    private String serviceName;
    private String credentialsId;
//    private String dashcredentialsId;

    private String dashUrl;
    private PrintStream printStream;
    private File root;
    private static String dashToken;

    // fields to support jenkins pipeline
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;
    private String username;
    private String password;
    private Long duration;
    private String pull_request_number;
    private String build_engine;
    // optional customized build number
    private String buildNumber;

 
    @DataBoundConstructor
    public PublishDashBuild(String hostName,String serviceName, String credentialsId, OptionalBuildInfo additionalBuildInfo) {
        this.credentialsId = credentialsId;
//        this.dashcredentialsId = dashcredentialsId;
//        this.userId = userId;
//        this.keyName = keyName;
        this.hostName = hostName;
        this.serviceName = serviceName;
        if (additionalBuildInfo == null) {
            this.buildNumber = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
        }
    }

    public PublishDashBuild(String pull_request_number,String build_engine,String gitRepo, String gitBranch, String gitCommit,String result,String hostName,String serviceName, String username, String password, Long duration) {
        this.pull_request_number = pull_request_number;
        this.build_engine = build_engine;
    	this.gitRepo = gitRepo;
        this.gitBranch = gitBranch;
        this.gitCommit = gitCommit;
        this.result = result;
        this.hostName = hostName;
        this.serviceName = serviceName;
        this.username = username;
        this.password = password;
        this.duration = duration;
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
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }
    
    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */   

    public String getCredentialsId() {
        return credentialsId;
    }
    
    public String getBuildNumber() {
        return buildNumber;
    }
    
    public String gethostName() {
        return hostName;
    }
    
    public String getserviceName() {
        return serviceName;
    }

    public static class OptionalBuildInfo {
        private String buildNumber;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber, String buildUrl) {
            this.buildNumber = buildNumber;
        }
    }

    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);
        
        Long duration = build.getParent().getLastCompletedBuild().getDuration();
        String Href = build.getParent().getLastCompletedBuild().getUrl();
        // create root dir for storing test result
        root = new File(build.getRootDir(), "DRA_TestResults");

        // Get the project name and build id from environment
        EnvVars envVars = build.getEnvironment(listener);

        if (!checkRootUrl(printStream)) {
            return;
        }

        // verify if user chooses advanced option to input customized DLMS
        String env = getDescriptor().getEnvironment();
        
        String targetDashAPI = chooseDashTargetAPI(env);
        this.dashUrl = targetDashAPI;

        //expand the variables
        this.duration = duration;
        this.hostName = envVars.expand(this.hostName);
        this.serviceName = envVars.expand(this.serviceName);

        // Check required parameters
        if (Util.isNullOrEmpty(hostName)) {
        	printStream.println("[IBM Cloud DevOps] Missing few required configurations");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Info.");
            return;
        }
       
        printStream.println("***************************************************************************"  );
        printStream.println("build Final Duration : " + this.duration);
        System.out.println("HREF : " + build.getParent().getLastCompletedBuild().getUrl());

        String dashToken;
        // get the Dash token
        try {
            if (Util.isNullOrEmpty(this.credentialsId)) {
                printStream.println("**********  Ist func ********"  );
          	dashToken = getDashToken(username, password, targetDashAPI);
            } else {
                printStream.println("**********  2nd func ********"  );
            	dashToken = getDashToken(build.getParent(), this.credentialsId, targetDashAPI);
            }    	

            printStream.println("[IBM Dash] Log in successfully, get the Dash token");
        } catch (Exception e) {
            printStream.println("[IBM Dash] Username/Password is not correct, fail to authenticate with Dash");
            printStream.println("[IBM Dash]" + e.toString());
            return;
        }
        printStream.println("Dash token " + dashToken);
        printStream.println("***************************************************************************");
        printStream.println("HREF : " + build.getParent().getLastCompletedBuild().getUrl());
        
        String link = chooseDashPOSTJenkinsTargetAPI(env);
        
        printStream.println("Link " + link);

        if (uploadDashBuildInfo(dashToken, build, envVars)) {
           // printStream.println("[IBM Cloud DevOps] Go to Control Center (" + link + ") to check your build status");
            BuildPublisherAction action = new BuildPublisherAction(link);
            build.addAction(action);
        }
    }
    
   

    /**
     * Construct the Git data model
     * @param envVars
     * @return
     */
    public BuildDashInfoModel.Repo buildGitRepo(EnvVars envVars) {
        String repoUrl = envVars.get("GIT_URL");
        String branch = envVars.get("GIT_BRANCH");
        String commitId = envVars.get("GIT_COMMIT");

        repoUrl = Util.isNullOrEmpty(repoUrl) ? this.gitRepo : repoUrl;
        branch = Util.isNullOrEmpty(branch) ? this.gitBranch : branch;
        commitId = Util.isNullOrEmpty(commitId) ? this.gitCommit : commitId;
        if (!Util.isNullOrEmpty(branch)) {
            String[] parts = branch.split("/");
            branch = parts[parts.length - 1];
        }

        BuildDashInfoModel.Repo repo = new BuildDashInfoModel.Repo(repoUrl, branch, commitId);
        return repo;
    }

    /**
     * Upload the build information to Dash.
     * @param dashToken
     * @param build
     * @param envVars
     * @throws IOException
     */
    private boolean uploadDashBuildInfo(String dashToken, Run build, EnvVars envVars) {
        String resStr = "";

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            String env = getDescriptor().getEnvironment();
            
            String targetDashJenkinsPOSTAPI = chooseDashPOSTJenkinsTargetAPI(env);
//            String url = targetDashJenkinsPOSTAPI;
            String url = "https://" + this.hostName + "/api/build/v1/services/{serviceName}/builds";
            url = url.replace("{serviceName}", URLEncoder.encode(this.serviceName, "UTF-8").replaceAll("\\+", "%20"));
            
            System.out.println("**********Final POST url : " + url + " *****************" );
            
            String buildNumber;
            if (Util.isNullOrEmpty(this.buildNumber)) {
                buildNumber = getBuildNumber(envVars.get("JOB_NAME"), build);
            } else {
                buildNumber = envVars.expand(this.buildNumber);
            }

            String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
                                              
            System.out.println("Duration of build3 : " + build.getDurationString() + "Estimates duration : " + build.getEstimatedDuration() );
            System.out.println("Duration of build4 : " + build.getDuration() + "more details : " + build.getTimestampString()  );
//            String Href = build.getParent().
          
            String buildStatus;
            Result result = build.getResult();
            if ((result != null && result.equals(Result.SUCCESS))
                    || (this.result != null && this.result.equals("SUCCESS"))) {
                buildStatus = "pass";
            } else {
                buildStatus = "fail";
            }
            		            		
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            TimeZone utc = TimeZone.getTimeZone("UTC");
            dateFormat.setTimeZone(utc);
            String timestamp = dateFormat.format(System.currentTimeMillis());
           
            Long DurationFinal = build.getParent().getLastCompletedBuild().getDuration();
            Long FinalDuration = DurationFinal * 1000000;
            System.out.println("Took final duration :" + FinalDuration);
            
//            int begin = dashToken.indexOf(":") + 2;
//            int end =  dashToken.lastIndexOf("}") - 1;
//            String dashToken1 = dashToken.substring(begin, end);
            
            HttpPost postMethod = new HttpPost(url);
            postMethod = addProxyInformation(postMethod);
            postMethod.setHeader("Content-Type","application/json");
            postMethod.setHeader("Authorization", "TOKEN " + dashToken);

            // build up the json body
            Gson gson = new Gson();
            BuildDashInfoModel.Repo repo = buildGitRepo(envVars);
            String repoBranch = repo.getBranch();
            String repoCommit = repo.getCommit_id();
            String repoRepoURL = repo.getRepository_url();
            String PRNum = "";
            String BuildEngine = "Jenkins";
            BuildDashInfoModel buildInfo = new BuildDashInfoModel(buildNumber, buildUrl, buildStatus, timestamp, FinalDuration, repo, repoBranch, repoCommit, repoRepoURL, PRNum, BuildEngine);
            
            System.out.println("Build Final Model : " + buildNumber + "  " + buildUrl + "  " + buildStatus + "  " + timestamp + "  " + DurationFinal + "  " + repo);
            System.out.println(buildInfo);
            System.out.println("GetREPO url " + buildInfo.getRepo_url());
            System.out.println("GetCommit " + buildInfo.getCommit());
            System.out.println("GetBranch " + buildInfo.getBranch());
            System.out.println("GetBuildEngine " + buildInfo.getBuild_engine());
            System.out.println("GetPRnumber " + buildInfo.getPull_request_number());
            System.out.println("Repo Branch " + repoBranch);
            System.out.println("Repo Commit " + repoCommit);
            System.out.println("REPO url " + repoRepoURL);
            System.out.println("PRNum " + PRNum);
            System.out.println("Build Engine " + BuildEngine);
            
            String json = gson.toJson(buildInfo);
            StringEntity data = new StringEntity(json);
            postMethod.setEntity(data);
            CloseableHttpResponse response = httpClient.execute(postMethod);
            resStr = EntityUtils.toString(response.getEntity());
            
            printStream.println("response for POST call for jekins build : " + resStr + response);
            
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                printStream.println("[IBM Dash] Upload Build Information successfully");
                return true;

            } else {
                // if gets error status
                printStream.println("[IBM Dash] Error: Failed to upload, response status " + response.getStatusLine());

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("user_error")) {
                    printStream.println("[IBM Dash] Reason: " + resJson.get("user_error"));
                }
            }
        } catch (JsonSyntaxException e) {
            printStream.println("[IBM Dash] Invalid Json response, response: " + resStr);
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
                printStream.println("[IBM Dash] Please check if you have the access to Organisation");
            
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public PublishDashBuild.PublishBuildActionImpl getDescriptor() {
        return (PublishDashBuild.PublishBuildActionImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishDashBuild}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/com/ibm/devops/dra/PublishDashBuild/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class PublishBuildActionImpl extends BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public PublishBuildActionImpl() {
            super(PublishDashBuild.class);
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'credentialId'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user.
         */

        private String environment;
        private boolean debug_mode;


        public FormValidation doCheckEnvironmentName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }
        
        public FormValidation doTestConnection1(@AncestorInPath ItemGroup context,
                @QueryParameter("credentialsId") final String credentialsId, @QueryParameter("userId") final String userId, @QueryParameter("keyName") final String keyName) {
        	String targetdashAPI = chooseDashTargetAPI(environment);

                try {
                    String newToken = getDashToken(context, credentialsId, targetdashAPI, userId, keyName);
                    dashToken = newToken;
                    String[] result = newToken.split(":");
                    System.out.println("^^^^^^^^^^^^^^^^^^^^^^^");
                    System.out.println("get Items : " + context.getItems());
                    System.out.println("get Display names : " + context.getDisplayName());
                    System.out.println("^^^^^^^^^^^^^^^^^^^^^^^");

                    System.out.println("newToken : " + newToken);
                    
                    int begin = newToken.indexOf(":") + 2;
                    int end =  newToken.lastIndexOf("}") - 1;
                    System.out.println("begin : " + begin);
                    System.out.println("end : " + end);
                    newToken = newToken.substring(begin, end);
                    System.out.println("NEW TOKEN : " + newToken);
                    if (Util.isNullOrEmpty(newToken)) {
                        return FormValidation.warning("<b>Got empty token</b>");
                    } else {
                        return FormValidation.okWithMarkup("<b>Connection successful with Dash</b>");                        
                    }
                } catch (Exception e) {
                	e.printStackTrace();
                    return FormValidation.error("Failed to log in to Dash, please check your username/password" + e + dashToken);
                }
        }
        
        public FormValidation doTestConnection2(@AncestorInPath ItemGroup context,
                @QueryParameter("credentialsId") final String credentialsId, @QueryParameter("userId") final String userId, @QueryParameter("keyName") final String keyName, @QueryParameter("accountName") final String accountName) {
        	String targetdashAPI = chooseDashTargetAPI(environment);

                try {
                    String newToken = getDashToken(context, credentialsId, targetdashAPI, userId, keyName);
                    dashToken = newToken;
                    String[] result = newToken.split(":");
                    System.out.println("^^^^^^^^^^^^^^^^^^^^^^^");
                    System.out.println("get Items : " + context.getItems());
                    System.out.println("get Display names : " + context.getDisplayName());
                    System.out.println("^^^^^^^^^^^^^^^^^^^^^^^");

                    System.out.println("newToken : " + newToken);
                    
                    int begin = newToken.indexOf(":") + 2;
                    int end =  newToken.lastIndexOf("}") - 1;
                    System.out.println("begin : " + begin);
                    System.out.println("end : " + end);
                    newToken = newToken.substring(begin, end);
                    System.out.println("NEW TOKEN TO ADD ACCOUNT: " + newToken);
                    JSONObject object = new JSONObject();
                    try {
                    	object.put("name", accountName);
                    	object.put("host", "");
                    }catch(Exception e) {
                    	e.printStackTrace();
                    }
                    
                    URL url = new URL("https://local-dash.gravitant.net/api/jenkins/v1/config/account");
//                    HttpProxyConfiguration configuration = buildProxyConfiguration(url);
                    CloseableHttpClient httpClient = HttpClients.createDefault();
                    String message = object.toString();
                    HttpPost postMethod = new HttpPost("https://local-dash.gravitant.net/api/jenkins/v1/config/account");
                    postMethod = addProxyInformation(postMethod);
                    postMethod.setHeader("Authorization", "TOKEN "+ newToken);
                    postMethod.setEntity(new StringEntity(message, "UTF8"));
                    
                   CloseableHttpResponse response = httpClient.execute(postMethod);
                   String token = EntityUtils.toString(response.getEntity(), "UTF-8");
                   System.out.println("Token Response for Account addition "+ token);
                    
                    if (response.getStatusLine().getStatusCode() != 200) {
                    	System.out.println("Response : " + response);
                        return FormValidation.warning("<b>Got error in adding account request</b>");
                    } else {
                        return FormValidation.okWithMarkup("<b>Account added successful with Dash</b>");                        
                    }
                } catch (Exception e) {
                	e.printStackTrace();
                    return FormValidation.error("Failed to add account in Dash, please check your username/password" + e + dashToken);
                }
        }

        

        /**
         * This method is called to populate the credentials list on the Jenkins config page.
         */
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context,
                                                     @QueryParameter("target") final String target) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            result.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(target).build()
                    )
            );
            return result;
        }            

        /**
         * Required Method
         * This is used to determine if this build step is applicable for your chosen project type. (FreeStyle, MultiConfiguration, Maven)
         * Some plugin build steps might be made to be only available to MultiConfiguration projects.
         *
         * @param aClass The current project
         * @return a boolean indicating whether this build step can be chose given the project type
         */
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            // return FreeStyleProject.class.isAssignableFrom(aClass);
            return true;
        }

        /**
         * Required Method
         * @return The text to be displayed when selecting your build in the project
         */
        public String getDisplayName() {
            return "Publish build information to IBM DevOps Intelligence";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            environment = formData.getString("environment");
            debug_mode = Boolean.parseBoolean(formData.getString("debug_mode"));
            save();
            return super.configure(req,formData);
        }

        public String getEnvironment() {
            return environment;
        }
        public boolean getDebugMode() {
            return debug_mode;
        }
    }
}



