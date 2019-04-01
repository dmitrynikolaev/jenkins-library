/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

import java.util.logging.Logger
import org.junit.*
import static org.hamcrest.CoreMatchers.*;
import org.zowe.jenkins_shared_library.integrationtest.*
import static groovy.test.GroovyAssert.*

/**
 * Test {@link org.zowe.jenkins_shared_library.scm.GitHub}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with parameter pointing to the library branch to test
 */
class GitHubTest extends IntegrationTest {
    @BeforeClass
    public static void initTestJob() {
        super.initTestJob()

        // init test job name
        _initTestJobName('github')

        // create test job
        def envVars = """GITHUB_USERNAME=${System.getProperty('github.username')}
GITHUB_EMAIL=${System.getProperty('github.email')}
GITHUB_CREDENTIAL=${System.getProperty('github.credential')}
"""
        def script = Utils.loadResource('/pipelines/githubTest.groovy')
        api.createJob(
            testJobName,
            'pipeline.xml',
            [Constants.INTEGRATION_TEST_JENKINS_FOLDER],
            [
                'fvt-env-vars'     : Utils.escapeXml(envVars),
                'fvt-script'       : Utils.escapeXml(script),
            ]
        )

        buildInformation = api.startJobAndGetBuildInformation(fullTestJobName, [
            'LIBRARY_BRANCH': System.getProperty('library.branch')
        ])

        if (buildInformation && buildInformation['number']) {
            buildLog = api.getBuildLog(fullTestJobName, buildInformation['number'])
        }
    }

    @AfterClass
    public static void deleteTestJob() {
        // delete the test job if exists
        if (api && testJobName) {
            // api.deleteJob(fullTestJobName)
        }
    }

    @Test
    void testBuildInformation() {
        assertThat('Build result', buildInformation, hasKey('number'));
        assertThat('Build result', buildInformation, hasKey('result'));
        assertThat('Build result', buildInformation['result'], equalTo('SUCCESS'));
        assertThat('Build console log', buildLog, not(equalTo('')))
    }

    @Test
    void testInit() {
        assertThat('Build console log', buildLog, containsString('[GITHUB_TEST] init successfully'))
    }

    @Test
    void testClone() {
        assertThat('Build console log', buildLog, containsString('[GITHUB_TEST] clone successfully'))
    }

    @Test
    void testCommit() {
        assertThat('Build console log', buildLog, containsString('[GITHUB_TEST] commit successfully'))
    }

    @Test
    void testPush() {
        assertThat('Build console log', buildLog, containsString('[GITHUB_TEST] push successfully'))
    }

    @Test
    void testTag() {
        assertThat('Build console log', buildLog, containsString('[GITHUB_TEST] tag successfully'))
    }
}
