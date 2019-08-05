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
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;
import java.util.HashSet;

/**
 * Authenticate with Bluemix and then upload the result file to DRA
 */
public class PublishDashTest extends AbstractDevOpsAction implements SimpleBuildStep {

    private final static String API_PART = "/organizations/{org_name}/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/results_multipart";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";

    // form fields from UI
    private final String lifecycleStage;
    private final String resultType;
    private String contents;
    private String additionalLifecycleStage;
    private String additionalContents;
    private String buildNumber;
    private String applicationName;
    private String buildJobName;
    private String orgName;
    private String toolchainName;
    private String environmentName;
    private String credentialsId;
    private String policyName;
    private boolean willDisrupt;
    private String hostName;
    private String serviceName;

    private EnvironmentScope testEnv;
    private String envName;
    private boolean isDeploy;

    private PrintStream printStream;
    private File root;
    private String dlmsUrl;
    private String draUrl;
    private static String bluemixToken;
    private static String dashToken;
    private static String preCredentials;

    //fields to support jenkins pipeline
    private String username;
    private String password;

    @DataBoundConstructor
    public PublishDashTest(String lifecycleStage,
    				   String resultType,
                       String contents,
                       String applicationName,
                       String orgName,
                       String toolchainName,
                       String buildJobName,
                       String credentialsId,
                       String hostName,
                       String serviceName,
                       OptionalUploadBlock additionalUpload,
                       OptionalBuildInfo additionalBuildInfo,
                       OptionalGate additionalGate,
                       EnvironmentScope testEnv) {
        this.lifecycleStage = lifecycleStage;
        this.resultType = resultType;
        this.contents = contents;
        this.credentialsId = credentialsId;
        this.applicationName = applicationName;
        this.orgName = orgName;
        this.toolchainName = toolchainName;
        this.buildJobName = buildJobName;
//        this.testEnv = testEnv;
//        this.envName = testEnv.getEnvName();
//        this.isDeploy = testEnv.isDeploy();
        this.hostName = hostName;
        this.serviceName = serviceName;

        if (additionalUpload == null) {
            this.additionalContents = null;
            this.additionalLifecycleStage = null;
        } else {
            this.additionalLifecycleStage = additionalUpload.additionalLifecycleStage;
            this.additionalContents = additionalUpload.additionalContents;
        }

        if (additionalBuildInfo == null) {
            this.buildNumber = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
        }

        if (additionalGate == null) {
            this.policyName = null;
            this.willDisrupt = false;
        } else {
            this.policyName = additionalGate.getPolicyName();
            this.willDisrupt = additionalGate.isWillDisrupt();
        }
    }

    public PublishDashTest(String lifecycleStage,
    		           String resultType,
                       String contents,
//                       String envName,
                       String orgName,
                       String applicationName,
                       String toolchainName,
                       String username,
                       String password,
                       String hostName,
                       String serviceName) {
        this.lifecycleStage = lifecycleStage;
        this.resultType = resultType;
        this.contents = contents;
//        this.envName = envName;
        this.applicationName = applicationName;
        this.orgName = orgName;
        this.toolchainName = toolchainName;
        this.username = username;
        this.password = password;
        this.hostName = hostName;
        this.serviceName = serviceName;
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApplicationName() {
        return applicationName;
    }

    public String getToolchainName() {
        return toolchainName;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public String getBuildJobName() {
        return buildJobName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }
    
    public String getHostName() {
        return hostName;
    }

    public String getServiceName() {
        return serviceName;
    }
    
    public String getLifecycleStage() {
        return lifecycleStage;
    }
    
    public String getResultType() {
        return resultType;
    }

    public String getContents() {
        return contents;
    }

    public String getAdditionalLifecycleStage() {
        return additionalLifecycleStage;
    }

    public String getAdditionalContents() {
        return additionalContents;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getPolicyName() {
        return policyName;
    }

    public boolean isWillDisrupt() {
        return willDisrupt;
    }

    public EnvironmentScope getTestEnv() {
        return testEnv;
    }

    public String getEnvName() {
        return envName;
    }

    public boolean isDeploy() {
        return isDeploy;
    }

    /**
     * Sub class for Optional Upload Block
     */
    public static class OptionalUploadBlock {
        private String additionalLifecycleStage;
        private String additionalContents;

        @DataBoundConstructor
        public OptionalUploadBlock(String additionalLifecycleStage, String additionalContents) {
            this.additionalLifecycleStage = additionalLifecycleStage;
            this.additionalContents = additionalContents;
        }
    }

    public static class OptionalBuildInfo {
        private String buildNumber;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber) {
            this.buildNumber = buildNumber;
        }
    }

    public static class OptionalGate {
        private String policyName;
        private boolean willDisrupt;

        @DataBoundConstructor
        public OptionalGate(String policyName, boolean willDisrupt) {
            this.policyName = policyName;
            setWillDisrupt(willDisrupt);
        }

        public String getPolicyName() {
            return policyName;
        }

        public boolean isWillDisrupt() {
            return willDisrupt;
        }

        @DataBoundSetter
        public void setWillDisrupt(boolean willDisrupt) {
            this.willDisrupt = willDisrupt;
        }
    }


    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        // create root dir for storing test result
        root = new File(build.getRootDir(), "DRA_TestResults");
        printStream.println("Root " + root);

        // Get the project name and build id from environment
        EnvVars envVars = build.getEnvironment(listener);

        if (!checkRootUrl(printStream)) {
            return;
        }

        // verify if user chooses advanced option to input customized DLMS
        String env = getDescriptor().getEnvironment();
        String targetAPI = chooseTargetAPI(env);
        String url = chooseDLMSUrl(env) + API_PART;
        // expand to support env vars
        this.orgName = envVars.expand(this.orgName);
        this.applicationName = envVars.expand(this.applicationName);
        this.toolchainName = envVars.expand(this.toolchainName);
        this.contents = envVars.expand(this.contents);
        this.hostName = envVars.expand(this.hostName);
        this.serviceName = envVars.expand(this.serviceName);
//        if (this.isDeploy || !Util.isNullOrEmpty(this.envName)) {
//            this.environmentName = envVars.expand(this.envName);
//        }

        String buildNumber, buildUrl;
        // if user does not specify the build number
        if (Util.isNullOrEmpty(this.buildNumber)) {
            // locate the build job that triggers current build
            Run triggeredBuild = getTriggeredBuild(build, buildJobName, envVars, printStream);
            if (triggeredBuild == null) {
                //failed to find the build job
                return;
            } else {
                if (Util.isNullOrEmpty(this.buildJobName)) {
                    // handle the case which the build job name left empty, and the pipeline case
                    this.buildJobName = envVars.get("JOB_NAME");
                }
                buildNumber = getBuildNumber(buildJobName, triggeredBuild);
            }
        } else {
            buildNumber = envVars.expand(this.buildNumber);
        }

//        url = url.replace("{org_name}", URLEncoder.encode(this.orgName, "UTF-8").replaceAll("\\+", "%20"));
//        url = url.replace("{toolchain_id}", URLEncoder.encode(this.toolchainName, "UTF-8").replaceAll("\\+", "%20"));
//        url = url.replace("{build_artifact}", URLEncoder.encode(this.applicationName, "UTF-8").replaceAll("\\+", "%20"));
//        url = url.replace("{build_id}", URLEncoder.encode(buildNumber, "UTF-8").replaceAll("\\+", "%20"));
//        this.dlmsUrl = url;

//        String link = chooseControlCenterUrl(env) + "deploymentrisk?orgName=" + URLEncoder.encode(this.orgName, "UTF-8") + "&toolchainId=" + this.toolchainName;

        url = "https://" + this.hostName + "/api/test/v1/services/{serviceName}/tests/{testType}";
        url = url.replace("{serviceName}", URLEncoder.encode(this.serviceName, "UTF-8").replaceAll("\\+", "%20"));
        url = url.replace("{testType}", URLEncoder.encode(this.resultType, "UTF-8").replaceAll("\\+", "%20"));

        String dashToken;
        // get the Dash token
        try {
            if (Util.isNullOrEmpty(this.credentialsId)) {
            	dashToken = getDashToken(username, password, targetAPI);
            } else {
            	dashToken = getDashToken(build.getParent(), this.credentialsId, targetAPI);
            }

            printStream.println("[IBM Cloud DevOps] Log in successfully, get the Dash token");
        } catch (Exception e) {
            printStream.println("[IBM Cloud DevOps] Username/Password is not correct, fail to authenticate with Dash");
            printStream.println("[IBM Cloud DevOps]" + e.toString());
            return;
        }
        
        printStream.println("Dash token " + dashToken);
        printStream.println("***************************************************************************");

        printStream.println("WorkSpace content  lifecycleStage" + workspace + contents + lifecycleStage);

        // parse the wildcard result files
        try {
            if(!scanAndUpload(build, workspace, contents, lifecycleStage, dashToken)){
                // if there is any error when scanning and uploading
                return;
            }

            // check to see if we need to upload additional result file
            if (!Util.isNullOrEmpty(additionalContents) && !Util.isNullOrEmpty(additionalLifecycleStage)) {
                if(!scanAndUpload(build, workspace, additionalContents, additionalLifecycleStage, dashToken)) {
                    return;
                }
            }
        } catch (Exception e) {
            printStream.print("[IBM Cloud DevOps] Got Exception: " + e.getMessage());
            e.printStackTrace();
            return;
        }

//        printStream.println("[IBM Cloud DevOps] Go to Control Center (" + link + ") to check your build status");

        // Gate
        // verify if user chooses advanced option to input customized DRA
        if (Util.isNullOrEmpty(policyName)) {
            return;
        }

        this.draUrl = chooseDRAUrl(env);

        // get decision response from DRA
        try {
            JsonObject decisionJson = getDecisionFromDRA(dashToken, buildNumber);
            if (decisionJson == null) {
                printStream.println("[IBM Cloud DevOps] get empty decision");
                return;
            }

            // retrieve the decision id to compose the report link
            String decisionId = String.valueOf(decisionJson.get("decision_id"));
            // remove the double quotes
            decisionId = decisionId.replace("\"","");

            // Show Proceed or Failed based on the decision
            String decision = String.valueOf(decisionJson.get("contents").getAsJsonObject().get("proceed"));
            if (decision.equals("true")) {
                decision = "Succeed";
            } else {
                decision = "Failed";
            }

            String cclink = chooseControlCenterUrl(env) + "deploymentrisk?orgName=" + URLEncoder.encode(this.orgName, "UTF-8") + "&toolchainId=" + this.toolchainName;

            String reportUrl = chooseReportUrl(env) + "decisionreport?orgName=" + URLEncoder.encode(this.orgName, "UTF-8") + "&toolchainId="
                    + URLEncoder.encode(toolchainName, "UTF-8") + "&reportId=" + decisionId;
            GatePublisherAction action = new GatePublisherAction(reportUrl, cclink, decision, this.policyName, build);
            build.addAction(action);

            printStream.println("************************************");
            printStream.println("Check IBM Cloud DevOps Gate Evaluation report here -" + reportUrl);
            printStream.println("Check IBM Cloud DevOps Deployment Risk Dashboard here -" + cclink);
            // console output for a "fail" decision
            if (decision.equals("Failed")) {
                printStream.println("IBM Cloud DevOps decision to proceed is:  false");
                printStream.println("************************************");
                if (willDisrupt) {
                    Result result = Result.FAILURE;
                    build.setResult(result);
                }
                return;
            }

            // console output for a "proceed" decision
            printStream.println("IBM Cloud DevOps decision to proceed is:  true");
            printStream.println("************************************");
            return;

        } catch (IOException e) {
            printStream.print("[IBM Cloud DevOps] Error: " + e.getMessage());
        }

    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Support wildcard for the result file path, scan the path and upload each matching result file to the DLMS
     * @param build - the current build
     * @param bluemixToken - the Bluemix toekn
     * @return false if there is any error when scan and upload the file
     */
    public boolean scanAndUpload(Run build, FilePath workspace, String path, String lifecycleStage, String dashToken) throws Exception {
        boolean errorFlag = true;
        FilePath[] filePaths = null;

        if (Util.isNullOrEmpty(path)) {
            // if no result file specified, create dummy result based on the build status
            filePaths = new FilePath[]{createDummyFile(build, workspace)};
        } else {

            // remove "./" prefix of the path if it exists
            if (path.startsWith("./")) {
                path = path.substring(2);
            }

            try {
                filePaths = workspace.list(path);
                printStream.println(" filePaths : " + filePaths.toString() + "  " + filePaths.length);
            } catch(InterruptedException ie) {
                printStream.println(" filePaths : " + filePaths);
                printStream.println("[IBM Cloud DevOps] catching interrupt" + ie.getMessage());
                ie.printStackTrace();
                throw ie;
            } catch (IOException e) {
                printStream.println("[IBM Cloud DevOps] catching act" + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }

        if (filePaths == null || filePaths.length < 1) {
            printStream.println("[IBM Cloud DevOps] Error: Fail to find the file, please check the path");
            return false;
        } else {

            for (FilePath fp : filePaths) {
                printStream.println(" Fp : " + fp);

                // make sure the file path is for file, and copy to the master build folder
                if (!fp.isDirectory()) {
                    FilePath resultFileLocation = new FilePath(new File(root, fp.getName()));
                    fp.copyTo(resultFileLocation);
                    printStream.println(" resultFileLocation : " + resultFileLocation);

                }

                //get timestamp
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                TimeZone utc = TimeZone.getTimeZone("UTC");
                dateFormat.setTimeZone(utc);
                String timestamp = dateFormat.format(System.currentTimeMillis());

                String rootUrl = Jenkins.getInstance().getRootUrl();
                String jobUrl = rootUrl + build.getUrl();
                
                printStream.println(" roturl : " + rootUrl);
                printStream.println(" jobUrl : " + jobUrl);

                
                // upload the result file to DLMS
                String res = sendFormToDLMS(dashToken, fp, lifecycleStage, jobUrl, timestamp);
                if(!printUploadMessage(res, fp.getName())) {
                    errorFlag = false;
                }
            }
        }

        return errorFlag;
    }

    /**
     * create a dummy result file following mocha format for some testing which does not generate test report
     * @param build - current build
     * @param workspace - current workspace, if it runs on slave, then it will be the path on slave
     * @return simple test result file
     */
    private FilePath createDummyFile(Run build, FilePath workspace) throws Exception {

        // if user did not specify the result file location, upload the dummy json file
        Gson gson = new Gson();

        //set the passes and failures based on the test status
        int passes, failures;
        Result result = build.getResult();
        if (result != null) {
            if (!result.equals(Result.SUCCESS)) {
                passes = 0;
                failures = 1;
            } else {
                passes = 1;
                failures = 0;
            }
        } else {
            throw new Exception("Failed to get build result");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        TimeZone utc = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(utc);
        String start = dateFormat.format(build.getStartTimeInMillis());
        long duration = build.getDuration();
        String end = dateFormat.format(build.getStartTimeInMillis() + duration);

        TestResultModel.Stats stats = new TestResultModel.Stats(1, 1, passes, 0, failures, start, end, duration);
        TestResultModel.Test test = new TestResultModel.Test("unknown test", "unknown test", duration, 0, null);
        TestResultModel.Test[] tests = {test};
        String[] emptyArray = {};
        TestResultModel testResultModel = new TestResultModel(stats, tests, emptyArray, emptyArray, emptyArray);

        // create new dummy file
        try {
            FilePath filePath = workspace.child("simpleTest.json");
            filePath.write(gson.toJson(testResultModel), "UTF8");
            return filePath;
        } catch (IOException e) {
            printStream.println("[IBM Cloud DevOps] Failed to create dummy file in current workspace, Exception: " + e.getMessage());
        }

        return null;
    }

    /**
     * print out the response message from DLMS to the console log
     * @param response - response from DLMS
     * @param fileName - uploaded filename
     * @return true if upload succeed, otherwise return false
     */
    private boolean printUploadMessage(String response, String fileName) {
        if (response.contains("Error")) {
            printStream.println("[IBM Cloud DevOps] " + response);
        } else if (response.contains("200")) {
            printStream.println("[IBM Cloud DevOps] Upload [" + fileName + "] SUCCESSFUL");
            return true;
        } else {
            printStream.println("[IBM Cloud DevOps]" + response + ", Upload [" + fileName + "] FAILED");
        }

        return false;
    }

    /**
     * * Send POST request to DLMS back end with the result file
     * @param bluemixToken - the Bluemix token
     * @param contents - the result file
     * @param jobUrl -  the build url of the build job in Jenkins
     * @param timestamp
     * @return - response/error message from DLMS
     */
    public String sendFormToDLMS(String dashToken, FilePath contents, String lifecycleStage, String jobUrl, String timestamp) throws IOException {
    	
    	String url = "https://" + this.hostName + "/api/test/v1/services/{serviceName}/tests/{testType}?fileType=" + this.resultType;
        url = url.replace("{serviceName}", URLEncoder.encode(this.serviceName, "UTF-8").replaceAll("\\+", "%20"));
        url = url.replace("{testType}", URLEncoder.encode(this.lifecycleStage, "UTF-8").replaceAll("\\+", "%20"));
        
        printStream.println("URL  : " + url);
        
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost postMethod = new HttpPost(url);

        postMethod = addProxyInformation(postMethod);
        // build up multi-part forms
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        if (contents != null) {

            File file = new File(root, contents.getName());
            FileBody fileBody = new FileBody(file);
            builder.addPart("contents", fileBody);
            printStream.println("file NAme1 : " + file.toString() + " " + file.getName());
            builder.addBinaryBody("uploadfile", file);
            printStream.println("file NAme2 : " + file.toString() + " " + file.getName());

            

            builder.addTextBody("test_artifact", file.getName());
//            if (this.isDeploy) {
//                builder.addTextBody("environment_name", environmentName);
//            }
            //Todo check the value of lifecycleStage
            builder.addTextBody("lifecycle_stage", lifecycleStage);
            builder.addTextBody("url", jobUrl);
            builder.addTextBody("timestamp", timestamp);

            String fileExt = FilenameUtils.getExtension(contents.getName());
            String contentType;
            switch (fileExt) {
                case "json":
                    contentType = CONTENT_TYPE_JSON;
                    break;
                case "xml":
                    contentType = CONTENT_TYPE_XML;
                    break;
                default:
                    return "Error: " + contents.getName() + " is an invalid result file type";
            }

            builder.addTextBody("contents_type", contentType);
            HttpEntity entity = builder.build();
            postMethod.setEntity(entity);
            postMethod.setHeader("Authorization", "TOKEN " + dashToken);
        } else {
            return "Error: File is null";
        }


        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(postMethod);
            // parse the response json body to display detailed info
            String resStr = EntityUtils.toString(response.getEntity());
            
            printStream.println("response for POST call for jekins build : " + resStr);
            printStream.println("response for POST call for jekins build : " + response);

            JsonParser parser = new JsonParser();
            JsonElement element =  parser.parse(resStr);

            if (!element.isJsonObject()) {
                // 401 Forbidden
                return "Error: Upload is Forbidden, please check your org name. Error message: " + element.toString();
            } else {
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("status")) {
                    return String.valueOf(response.getStatusLine()) + "\n" + resJson.get("status");
                } else {
                    // other cases
                    return String.valueOf(response.getStatusLine());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }


    /**
     * Send a request to DRA backend to get a decision
     * @param buildId - build ID, get from Jenkins environment
     * @return - the response decision Json file
     */
    private JsonObject getDecisionFromDRA(String bluemixToken, String buildId) throws IOException {
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();

        String url = this.draUrl;
        url = url + "/organizations/" + orgName +
                "/toolchainids/" + toolchainName +
                "/buildartifacts/" + URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20") +
                "/builds/" + buildId +
                "/policies/" + URLEncoder.encode(policyName, "UTF-8").replaceAll("\\+", "%20") +
                "/decisions";
//        if (this.isDeploy) {
//            url = url.concat("?environment_name=" + environmentName);
//        }

        HttpPost postMethod = new HttpPost(url);

        postMethod = addProxyInformation(postMethod);
        postMethod.setHeader("Authorization", bluemixToken);
        postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

        CloseableHttpResponse response = httpClient.execute(postMethod);
        String resStr = EntityUtils.toString(response.getEntity());

        try {
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                printStream.println("[IBM Cloud DevOps] Get decision successfully");
                return resJson;
            } else {
                // if gets error status
                printStream.println("[IBM Cloud DevOps] Error: Failed to get a decision, response status " + response.getStatusLine());

                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("message")) {
                    printStream.println("[IBM Cloud DevOps] Reason: " + resJson.get("message"));
                }
            }
        } catch (JsonSyntaxException e) {
            printStream.println("[IBM Cloud DevOps] Invalid Json response, response: " + resStr);
        }

        return null;

    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public PublishDashTest.PublishTestImpl getDescriptor() {
        return (PublishDashTest.PublishTestImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishTest}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/com/ibm/devops/dra/PublishTest/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class PublishTestImpl extends BuildStepDescriptor<Publisher> {
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
        public PublishTestImpl() {
            super(PublishDashTest.class);
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

        public FormValidation doCheckOrgName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckApplicationName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckToolchainName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value == null || value.equals("empty")) {
                return FormValidation.errorWithMarkup("Could not retrieve list of toolchains. Please check your username and password. If you have not created a toolchain, create one <a target='_blank' href='https://console.ng.bluemix.net/devops/create'>here</a>.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckEnvironmentName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPolicyName(@QueryParameter String value) {

            if (value == null || value.equals("empty")) {
                return FormValidation.errorWithMarkup("Fail to get the policies, please check your username/password or org name and make sure you have created policies for this org and toolchain.");
            }
            return FormValidation.ok();
        }

        public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
                                               @QueryParameter("credentialsId") final String credentialsId) {
            String targetAPI = chooseTargetAPI(environment);
            if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                preCredentials = credentialsId;
                try {
                    String bluemixToken = getBluemixToken(context, credentialsId, targetAPI);
                    if (Util.isNullOrEmpty(bluemixToken)) {
                        PublishDashTest.bluemixToken = bluemixToken;
                        return FormValidation.warning("<b>Got empty token</b>");
                    } else {
                        return FormValidation.okWithMarkup("<b>Connection successful</b>");
                    }
                } catch (Exception e) {
                    return FormValidation.error("Failed to log in to Bluemix, please check your username/password");
                }
            } else {

                return FormValidation.okWithMarkup("<b>Connection successful</b>");
            }
        }

        /**
         * Autocompletion for build job name field
         * @param value - user input for the build job name field
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteBuildJobName(@QueryParameter String value) {
            AutoCompletionCandidates auto = new AutoCompletionCandidates();

            // get all jenkins job
            List<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
            HashSet<String> jobSet = new HashSet<>();
            for (int i = 0; i < jobs.size(); i++) {
                String jobName = jobs.get(i).getName();

                if (jobName.toLowerCase().startsWith(value.toLowerCase())) {
                    jobSet.add(jobName);
                }
            }

            for (String s : jobSet) {
                auto.add(s);
            }

            return auto;
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
         * This method is called to populate the policy list on the Jenkins config page.
         * @param context
         * @param orgName
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillPolicyNameItems(@AncestorInPath ItemGroup context,
                                                  @RelativePath("..") @QueryParameter final String orgName,
                                                  @RelativePath("..") @QueryParameter final String toolchainName,
                                                  @RelativePath("..") @QueryParameter final String credentialsId) {
            String targetAPI = chooseTargetAPI(environment);
            try {
                // if user changes to a different credential, need to get a new token
                if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                    bluemixToken = getBluemixToken(context, credentialsId, targetAPI);
                    preCredentials = credentialsId;
                }
            } catch (Exception e) {
                return new ListBoxModel();
            }
            if(debug_mode){
                LOGGER.info("#######UPLOAD TEST RESULTS : calling getPolicyList#######");
            }
            return getPolicyList(bluemixToken, orgName, toolchainName, environment, debug_mode);
        }

        /**
         * This method is called to populate the toolchain list on the Jenkins config page.
         * @param context
         * @param orgName
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillToolchainNameItems(@AncestorInPath ItemGroup context,
                                                     @QueryParameter("credentialsId") final String credentialsId,
                                                     @QueryParameter("orgName") final String orgName) {
            String targetAPI = chooseTargetAPI(environment);
            try {
                bluemixToken = getBluemixToken(context, credentialsId, targetAPI);
            } catch (Exception e) {
                return new ListBoxModel();
            }
            if(debug_mode){
                LOGGER.info("#######UPLOAD TEST RESULTS : calling getToolchainList#######");
            }
            ListBoxModel toolChainListBox = getToolchainList(bluemixToken, orgName, environment, debug_mode);
            return toolChainListBox;

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

        public ListBoxModel doFillLifecycleStageItems(@QueryParameter("lifecycleStage") final String selection) {
            return fillTestType();
        }
        
        public ListBoxModel doFillResultTypeItems(@QueryParameter("resultType") final String selection) {
            return fillResultType();
        }

        public ListBoxModel doFillAdditionalLifecycleStageItems(@QueryParameter("additionalLifecycleStage") final String selection) {
            return fillTestType();
        }

        /**
         * fill the dropdown list of rule type
         * @return the dropdown list model
         */
        public ListBoxModel fillTestType() {
            ListBoxModel model = new ListBoxModel();

            model.add("Unit Test", "unit");
            model.add("Functional Verification Test", "fvt");
            model.add("Integration Test", "integrationtest");
            model.add("Security Test", "securitytest");
            model.add("Performance Test", "performancetest");

            return model;
        }
        
        /**
         * fill the dropdown list of rule type
         * @return the dropdown list model
         */
        public ListBoxModel fillResultType() {
            ListBoxModel model = new ListBoxModel();

            model.add("JUnit", "junit");
            model.add("JMeter", "jmeter");
            model.add("Pass/Fail", "status");
            return model;
        }

        /**
         * Required Method
         * @return The text to be displayed when selecting your build in the project
         */
        public String getDisplayName() {
            return "Publish test result to IBM DevOps Intelligence";
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
