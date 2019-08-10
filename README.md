# IBM Cloud DevOps

---

With this Jenkins plugin, You can publish build & test results to DevOps Intelligence.

This plugin provides Jenkinsfile and Post-Build Actions to support pipeline & non-pipeline projects respectively.

## 1. Create a service token in Dash

Before you can integrate DevOps Intelligence with a Jenkins project, you must create a service tokens for build & test in dash.

1. Add token as credentials in jenkins as username-password

### General workflow

1. Open the configuration of any jobs that you have, such as build, test, or deployment.

2. Add a post-build action for the corresponding type:

   * For build jobs, use **Publish build information to IBM Cloud Intelligence**.

   * For test jobs, use **Publish test result to IBM Cloud Intelligence**.

3. Complete the required fields:

   * From the **DevOps Intelligence Credentials**, select your service token. If they are not saved in Jenkins, click **Add** to add as username-password and save them. 

   * In the **Host Name** field, specify devOps Intelligence hostname.

   * Specify Service name in **Service Name** field

   * For the **Result File Location** field, specify the result file's location. If the test doesn't generate a result file, leave this field empty. The plugin uploads a default result file that is based on the status of current test job.

   * Specify Result type (Junit/ Jmeter/ Pass-Fail). Leave **Result File Location** field blank if result type is Pass-Fail
   
   * Specify Test type (unit)

## License

Copyright&copy; 2016, 2017 IBM Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
