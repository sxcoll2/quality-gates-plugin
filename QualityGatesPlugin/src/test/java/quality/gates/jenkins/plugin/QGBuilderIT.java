package quality.gates.jenkins.plugin;

import hudson.model.*;
import jenkins.model.GlobalConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;

public class QGBuilderIT {

    public static final String TEST_NAME = "TestName";
    private QGBuilder qgBuilder;

    private JobConfigData jobConfigData;

    private FreeStyleProject freeStyleProject;

    private GlobalConfig globalConfig;

    @Mock
    private BuildDecision buildDecision;

    private JobConfigurationService jobConfigurationService;

    private JobExecutionService jobExecutionService;

    private GlobalConfigDataForSonarInstance globalConfigDataForSonarInstance;

    private List<GlobalConfigDataForSonarInstance> globalConfigDataForSonarInstanceList;

    private QGBuilderDescriptor builderDescriptor;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        jobConfigData = new JobConfigData();
        jobConfigurationService = new JobConfigurationService();
        jobExecutionService = new JobExecutionService();
        globalConfigDataForSonarInstance = new GlobalConfigDataForSonarInstance();
        builderDescriptor = new QGBuilderDescriptor(jobExecutionService, jobConfigurationService);
        qgBuilder = new QGBuilder(jobConfigData, buildDecision, jobExecutionService, jobConfigurationService, globalConfigDataForSonarInstance);
        globalConfig = GlobalConfiguration.all().get(GlobalConfig.class);
        freeStyleProject = jenkinsRule.createFreeStyleProject("freeStyleProject");
        freeStyleProject.getBuildersList().add(qgBuilder);
        globalConfigDataForSonarInstanceList = new ArrayList<>();
    }

    @Test
    public void testPrebuildShouldFailBuildNoGlobalConfigWithSameName() throws Exception{
        doReturn(null).when(buildDecision).chooseSonarInstance(globalConfig, jobConfigData);
        jobConfigData.setSonarInstanceName(TEST_NAME);
        jenkinsRule.assertBuildStatus(Result.FAILURE, buildProject(freeStyleProject));
        Run lastRun = freeStyleProject._getRuns().newestValue();
        jenkinsRule.assertLogContains("'TestName' no longer exists.", lastRun);
    }

    @Test
    public void testPerformShouldSucceedWithNoWarning() throws Exception {
        setGlobalConfigDataAndJobConfigDataNames(TEST_NAME, TEST_NAME);
        jobConfigData.setProjectKey("projectKey");
        doReturn(globalConfigDataForSonarInstance).when(buildDecision).chooseSonarInstance(globalConfig, jobConfigData);
        doReturn(Result.SUCCESS).when(buildDecision).getStatus(any(GlobalConfigDataForSonarInstance.class),any(JobConfigData.class));
        jenkinsRule.buildAndAssertSuccess(freeStyleProject);
        Run lastRun = freeStyleProject._getRuns().newestValue();
        jenkinsRule.assertLogContains("build passed: TRUE", lastRun);
    }

    @Test
    public void testPerformShouldSucceedWithWarning() throws Exception {
        setGlobalConfigDataAndJobConfigDataNames("","");
        jobConfigData.setProjectKey("projectKey");
        doReturn(globalConfigDataForSonarInstance).when(buildDecision).chooseSonarInstance(globalConfig, jobConfigData);
        doReturn(Result.SUCCESS).when(buildDecision).getStatus(any(GlobalConfigDataForSonarInstance.class),any(JobConfigData.class));
        jenkinsRule.buildAndAssertSuccess(freeStyleProject);
        Run lastRun = freeStyleProject._getRuns().newestValue();
        jenkinsRule.assertLogContains(JobExecutionService.DEFAULT_CONFIGURATION_WARNING, lastRun);
        jenkinsRule.assertLogContains("build passed: TRUE", lastRun);
    }

    @Test
    public void testPerformShouldSucceedUnstableWithNoWarning() throws Exception {
        setGlobalConfigDataAndJobConfigDataNames(TEST_NAME, TEST_NAME);
        jobConfigData.setProjectKey("projectKey");
        doReturn(globalConfigDataForSonarInstance).when(buildDecision).chooseSonarInstance(globalConfig, jobConfigData);
        doReturn(Result.UNSTABLE).when(buildDecision).getStatus(any(GlobalConfigDataForSonarInstance.class),any(JobConfigData.class));
        jenkinsRule.assertBuildStatus(Result.UNSTABLE, buildProject(freeStyleProject));
        Run lastRun = freeStyleProject._getRuns().newestValue();
        jenkinsRule.assertLogContains("build passed: TRUE", lastRun);
    }

    @Test
    public void testPerformShouldSucceedUnstableWithWarning() throws Exception {
        setGlobalConfigDataAndJobConfigDataNames("","");
        jobConfigData.setProjectKey("projectKey");
        doReturn(globalConfigDataForSonarInstance).when(buildDecision).chooseSonarInstance(globalConfig, jobConfigData);
        doReturn(Result.UNSTABLE).when(buildDecision).getStatus(any(GlobalConfigDataForSonarInstance.class),any(JobConfigData.class));
        jenkinsRule.assertBuildStatus(Result.UNSTABLE, buildProject(freeStyleProject));
        Run lastRun = freeStyleProject._getRuns().newestValue();
        jenkinsRule.assertLogContains(JobExecutionService.DEFAULT_CONFIGURATION_WARNING, lastRun);
        jenkinsRule.assertLogContains("build passed: TRUE", lastRun);
    }

    @Test
    public void testPerformShouldFail() throws Exception {
        setGlobalConfigDataAndJobConfigDataNames(TEST_NAME, TEST_NAME);
        jobConfigData.setProjectKey("projectKey");
        doReturn(globalConfigDataForSonarInstance).when(buildDecision).chooseSonarInstance(globalConfig, jobConfigData);
        doReturn(Result.FAILURE).when(buildDecision).getStatus(any(GlobalConfigDataForSonarInstance.class),any(JobConfigData.class));
        jenkinsRule.assertBuildStatus(Result.FAILURE, buildProject(freeStyleProject));
        Run lastRun = freeStyleProject._getRuns().newestValue();
        jenkinsRule.assertLogContains("build passed: FALSE", lastRun);
    }

    @Test
    public void testPerformShouldCatchQGException() throws Exception {
        setGlobalConfigDataAndJobConfigDataNames(TEST_NAME, TEST_NAME);
        jobConfigData.setProjectKey("projectKey");
        QGException exception = new QGException("TestException");
        doReturn(globalConfigDataForSonarInstance).when(buildDecision).chooseSonarInstance(globalConfig, jobConfigData);
        doThrow(exception).when(buildDecision).getStatus(any(GlobalConfigDataForSonarInstance.class),any(JobConfigData.class));
        jenkinsRule.assertBuildStatus(Result.FAILURE, buildProject(freeStyleProject));
        Run lastRun = freeStyleProject._getRuns().newestValue();
        jenkinsRule.assertLogContains("QGException", lastRun);
    }

    private void setGlobalConfigDataAndJobConfigDataNames(String firstInstanceName, String secondInstanceName) {
        globalConfigDataForSonarInstance.setName(firstInstanceName);
        jobConfigData.setSonarInstanceName(secondInstanceName);
        globalConfig.setGlobalConfigDataForSonarInstances(globalConfigDataForSonarInstanceList);
    }

    private FreeStyleBuild buildProject(FreeStyleProject freeStyleProject) throws InterruptedException, ExecutionException {
        return freeStyleProject.scheduleBuild2(0).get();
    }
}
