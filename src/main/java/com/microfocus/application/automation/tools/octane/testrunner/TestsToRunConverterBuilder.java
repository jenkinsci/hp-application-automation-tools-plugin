/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2021 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.testrunner;

import com.hp.octane.integrations.executor.TestsToRunConverterResult;
import com.hp.octane.integrations.executor.TestsToRunConvertersFactory;
import com.hp.octane.integrations.executor.TestsToRunFramework;
import com.hp.octane.integrations.executor.converters.MbtTest;
import com.hp.octane.integrations.executor.converters.MfUftConverter;
import com.hp.octane.integrations.utils.SdkStringUtils;
import com.microfocus.application.automation.tools.AlmToolsUtils;
import com.microfocus.application.automation.tools.model.TestsFramework;
import com.microfocus.application.automation.tools.octane.configuration.ConfigurationValidator;
import com.microfocus.application.automation.tools.octane.executor.UftConstants;
import com.microfocus.application.automation.tools.octane.model.processors.projects.JobProcessorFactory;
import hudson.*;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URL;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.microfocus.application.automation.tools.run.RunFromFileBuilder.HP_TOOLS_LAUNCHER_EXE;

/**
 * Builder for available frameworks for converting
 */
public class TestsToRunConverterBuilder extends Builder implements SimpleBuildStep {

    private TestsToRunConverterModel framework;

    public static final String TESTS_TO_RUN_PARAMETER = "testsToRun";

    private static final String DEFAULT_EXECUTING_DIRECTORY = "${workspace}";
    private static final String CHECKOUT_DIRECTORY_PARAMETER = "testsToRunCheckoutDirectory";

    public TestsToRunConverterBuilder(String framework) {
        this.framework = new TestsToRunConverterModel(framework, "");
    }

    @DataBoundConstructor
    public TestsToRunConverterBuilder(String framework, String format) {
        this.framework = new TestsToRunConverterModel(framework, format);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        try {
            ParametersAction parameterAction = build.getAction(ParametersAction.class);
            String rawTests = null;
            String executingDirectory = DEFAULT_EXECUTING_DIRECTORY;
            if (parameterAction != null) {
                ParameterValue suiteIdParameter = parameterAction.getParameter(UftConstants.SUITE_ID_PARAMETER_NAME);
                if (suiteIdParameter != null) {
                    printToConsole(listener, UftConstants.SUITE_ID_PARAMETER_NAME + " : " + suiteIdParameter.getValue());
                }
                ParameterValue suiteRunIdParameter = parameterAction.getParameter(UftConstants.SUITE_RUN_ID_PARAMETER_NAME);
                if (suiteRunIdParameter != null) {
                    printToConsole(listener, UftConstants.SUITE_RUN_ID_PARAMETER_NAME + " : " + suiteRunIdParameter.getValue());
                }

                ParameterValue executionIdParameter = parameterAction.getParameter(UftConstants.EXECUTION_ID_PARAMETER_NAME);
                if (executionIdParameter != null) {
                    printToConsole(listener, UftConstants.EXECUTION_ID_PARAMETER_NAME + " : " + executionIdParameter.getValue());
                }


                ParameterValue testsParameter = parameterAction.getParameter(TESTS_TO_RUN_PARAMETER);
                if (testsParameter != null && testsParameter.getValue() instanceof String) {
                    rawTests = (String) testsParameter.getValue();
                    printToConsole(listener, TESTS_TO_RUN_PARAMETER + " found with value : " + rawTests);
                }

                ParameterValue checkoutDirParameter = parameterAction.getParameter(CHECKOUT_DIRECTORY_PARAMETER);
                if (checkoutDirParameter != null) {
                    if (testsParameter.getValue() instanceof String && StringUtils.isNotEmpty((String) checkoutDirParameter.getValue())) {
                        executingDirectory = (String) checkoutDirParameter.getValue();//"%" + CHECKOUT_DIRECTORY_PARAMETER + "%";
                        printToConsole(listener, CHECKOUT_DIRECTORY_PARAMETER + " parameter found with value : " + executingDirectory);
                    } else {
                        printToConsole(listener, CHECKOUT_DIRECTORY_PARAMETER + " parameter found, but its value is empty or its type is not String. Using default value.");
                    }
                }
                printToConsole(listener, "checkout directory : " + executingDirectory);
            }
            if (StringUtils.isEmpty(rawTests)) {
                printToConsole(listener, TESTS_TO_RUN_PARAMETER + " is not found or has empty value. Skipping.");
                return;
            }

            if (framework == null || SdkStringUtils.isEmpty(getFramework())) {
                printToConsole(listener, "No frameworkModel is selected. Skipping.");
                return;
            }
            String frameworkName = getFramework();
            String frameworkFormat = getFormat();
            printToConsole(listener, "Selected framework = " + frameworkName);
            if (SdkStringUtils.isNotEmpty(frameworkFormat)) {
                printToConsole(listener, "Using format = " + frameworkFormat);
            }


            TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(frameworkName);
            boolean isMbt = rawTests.contains("mbtData");
            TestsToRunConverterResult convertResult = null;
            if (isMbt) {
                //MBT needs to know real path to tests and not ${workspace}
                //MBT needs to run on slave  to extract function libraries from checked out files
                try {
                    EnvVars env = build.getEnvironment(listener);
                    executingDirectory = env.expand(executingDirectory);
                } catch (IOException | InterruptedException e) {
                    listener.error("Failed loading build environment " + e);
                }
                convertResult = filePath.act(new GetConvertResult(testsToRunFramework, frameworkFormat, rawTests, executingDirectory));
            } else {
                convertResult = (new GetConvertResult(testsToRunFramework, frameworkFormat, rawTests, executingDirectory)).invoke(null, null);
            }

            if (convertResult.getMbtTests() != null) {
                createMTBTests(convertResult.getMbtTests(), build, filePath, launcher, listener);
            }
            printToConsole(listener, "Found #tests : " + convertResult.getTestsData().size());
            printToConsole(listener, "Set to parameter : " + convertResult.getTestsToRunConvertedParameterName() + " = " + convertResult.getConvertedTestsString());
            printToConsole(listener, "********************* Conversion is done *********************");
            if (JobProcessorFactory.WORKFLOW_RUN_NAME.equals(build.getClass().getName())) {
                List<ParameterValue> newParams = (parameterAction != null) ? new ArrayList<>(parameterAction.getAllParameters()) : new ArrayList<>();
                newParams.add(new StringParameterValue(convertResult.getTestsToRunConvertedParameterName(), convertResult.getConvertedTestsString()));
                ParametersAction newParametersAction = new ParametersAction(newParams);
                build.addOrReplaceAction(newParametersAction);
            } else {
                VariableInjectionAction via = new VariableInjectionAction(convertResult.getTestsToRunConvertedParameterName(), convertResult.getConvertedTestsString());
                build.addAction(via);
            }
        } catch (IllegalArgumentException e) {
            printToConsole(listener, "Failed to convert : " + e.getMessage());
            build.setResult(Result.FAILURE);

            return;
        }
    }

    private void createMTBTests(List<MbtTest> tests, @Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        build.getRootDir();
        Properties props = new Properties();
        props.setProperty("runType", "MBT");
        props.setProperty("resultsFilename", "must be here");

        EnvVars env = build.getEnvironment(listener);

        props.setProperty("parentFolder", workspace.getRemote() +"\\" + MfUftConverter.MBT_PARENT_SUB_DIR);
        props.setProperty("repoFolder", workspace.getRemote());
        ParametersAction parameterAction = build.getAction(ParametersAction.class);
        ParameterValue checkoutDirParameter = parameterAction.getParameter(CHECKOUT_DIRECTORY_PARAMETER);
        if (checkoutDirParameter != null) {
            props.setProperty("parentFolder", env.expand((String)checkoutDirParameter.getValue()) +"\\" + MfUftConverter.MBT_PARENT_SUB_DIR);
            props.setProperty("repoFolder",  env.expand((String)checkoutDirParameter.getValue()));
        }

        int counter = 1;

        for (MbtTest mbtTest : tests) {
            props.setProperty("test" + counter, mbtTest.getName());
            props.setProperty("package" + counter, "_" + counter);
            props.setProperty("script" + counter, env.expand(mbtTest.getScript()));
            props.setProperty("unitIds" + counter, mbtTest.getUnitIds().stream().map( n -> n.toString() ).collect(Collectors.joining(";" ) ));
            props.setProperty("underlyingTests" + counter, env.expand((String.join(";", mbtTest.getUnderlyingTests()))));
            props.setProperty("functionLibraries" + counter, env.expand((String.join(";", mbtTest.getFunctionLibraries()))));
            props.setProperty("recoveryScenarios" + counter, env.expand((String.join(";", mbtTest.getRecoveryScenarios()))));

            if (mbtTest.getEncodedIterations() != null && !mbtTest.getEncodedIterations().isEmpty()) {
                //Expects to receive params in CSV format, encoded base64, for example Y29sMSxjb2wyCjEsMwoyLDQK
                props.setProperty("datableParams" + counter, mbtTest.getEncodedIterations());
            }
            counter++;
        }

        //prepare time
        Date now = new Date();
        Format formatter = new SimpleDateFormat("ddMMyyyyHHmmssSSS");
        String time = formatter.format(now);

        // get properties serialized into a stream
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            props.store(stream, "");
        } catch (IOException e) {
            listener.error("Storing props failed: " + e);
            build.setResult(Result.FAILURE);
        }
        String propsSerialization = stream.toString();
        InputStream propsStream = new ByteArrayInputStream(propsSerialization.getBytes());
        String paramFileName = "mbt_props" + time + ".txt";
        FilePath propsFileName = workspace.child(paramFileName);

        //HP Tool Launcher
        URL cmdExeUrl = Jenkins.get().pluginManager.uberClassLoader.getResource(HP_TOOLS_LAUNCHER_EXE);
        if (cmdExeUrl == null) {
            listener.fatalError(HP_TOOLS_LAUNCHER_EXE + " not found in resources");
            return;
        }
        FilePath cmdLineExe = workspace.child(HP_TOOLS_LAUNCHER_EXE);


        try {
            // create a file for the properties file, and save the properties
            propsFileName.copyFrom(propsStream);
            printToConsole(listener, "MBT props file saved to " + propsFileName.getRemote());

            // Copy the script to the project workspace
            if (!cmdLineExe.exists()) {
                cmdLineExe.copyFrom(cmdExeUrl);
                printToConsole(listener, "HPToolLauncher copied to " + cmdLineExe.getRemote());
            }

        } catch (IOException | InterruptedException e) {
            build.setResult(Result.FAILURE);
            listener.error("Copying executable files to executing node " + e);
        }

        try {
            // Run the HpToolsLauncher.exe
            AlmToolsUtils.runOnBuildEnv(build, launcher, listener, cmdLineExe, paramFileName);
            // Has the report been successfully generated?
        } catch (IOException ioe) {
            Util.displayIOException(ioe, listener);
            build.setResult(Result.FAILURE);
            listener.error("Failed running HpToolsLauncher " + ioe);
            return;
        }
    }

    /***
     * Used in UI
     * @return
     */
    public TestsToRunConverterModel getTestsToRunConverterModel() {
        return framework;
    }

    /***
     * Used in Pipeline
     * @return
     */
    public String getFramework() {
        return framework.getFramework().getName();
    }

    public String getFormat() {
        return framework.getFramework().getFormat();
    }

    public boolean getIsCustom() {
        return framework != null && TestsToRunFramework.Custom.value().equals(framework.getFramework().getName());
    }

    private static void printToConsole(TaskListener listener, String msg) {
        listener.getLogger().println(TestsToRunConverterBuilder.class.getSimpleName() + " : " + msg);
    }

    private static class GetConvertResult implements FilePath.FileCallable<TestsToRunConverterResult>{

        private TestsToRunFramework framework;
        private String rawTests;
        private String executingDirectory;
        private String format;

        public GetConvertResult(TestsToRunFramework framework, String format, String rawTests,String executingDirectory){
            this.framework=framework;
            this.rawTests=rawTests;
            this.format=format;
            this.executingDirectory=executingDirectory;
        }
        @Override
        public TestsToRunConverterResult invoke(File file, VirtualChannel virtualChannel) throws IOException, InterruptedException {
            return  TestsToRunConvertersFactory.createConverter(framework)
                    .setFormat(format)
                    .convert(rawTests, executingDirectory);
        }

        @Override
        public void checkRoles(RoleChecker roleChecker) throws SecurityException {
            //no need to check roles as this can be run on master and on slave
        }
    }


    @Symbol("convertTestsToRun")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;//FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "ALM Octane testing framework converter";
        }

        public FormValidation doTestConvert(
                @QueryParameter("testsToRun") String rawTests,
                @QueryParameter("teststorunconverter.framework") String framework,
                @QueryParameter("teststorunconverter.format") String format) {

            try {

                if (StringUtils.isEmpty(rawTests)) {
                    throw new IllegalArgumentException("'Tests to run' parameter is missing");
                }

                if (StringUtils.isEmpty(framework)) {
                    throw new IllegalArgumentException("'Framework' parameter is missing");
                }

                TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(framework);
                if (TestsToRunFramework.Custom.equals(testsToRunFramework) && StringUtils.isEmpty(format)) {
                    throw new IllegalArgumentException("'For.convertmat' parameter is missing");
                }

                TestsToRunConverterResult convertResult = TestsToRunConvertersFactory.createConverter(testsToRunFramework)
                        .setFormat(format)
                        .convert(rawTests, TestsToRunConverterBuilder.DEFAULT_EXECUTING_DIRECTORY);
                String result = Util.escape(convertResult.getConvertedTestsString());
                return ConfigurationValidator.wrapWithFormValidation(true, "Conversion is successful : <div style=\"margin-top:20px\">" + result + "</div>");
            } catch (Exception e) {
                return ConfigurationValidator.wrapWithFormValidation(false, "Failed to convert : " + e.getMessage());
            }
        }

        /**
         * Gets report archive modes.
         *
         * @return the report archive modes
         */
        public List<TestsFramework> getFrameworks() {

            return TestsToRunConverterModel.Frameworks;
        }

    }
}
