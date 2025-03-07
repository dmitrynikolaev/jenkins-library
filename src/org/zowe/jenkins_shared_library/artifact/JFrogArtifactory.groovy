/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.artifact

import java.net.URLEncoder
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.exceptions.UnderConstructionException
import org.zowe.jenkins_shared_library.Utils

/**
 * Operating artifacts with jFrog Artifatory CLI commands or Jenkins Plugin.
 *
 * @Example
 * <pre>
 *     def jfrog = new JFrogArtifactory(this)
 *     jfrog.init(
 *         url: 'https://gizaartifactory.jfrog.io/gizaartifactory',
 *         usernamePasswordCredential: 'my-artifactory-credential'
 *     )
 *     jfrog.upload(
 *         pattern : 'build/result/of/my-project.zip',
 *         target  : 'lib-snapshot-local/org/zowe/my-project/0.1.2/my-project-0.1.2-snapshot.zip'
 *     )
 * </pre>
 */
class JFrogArtifactory implements ArtifactInterface {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * CLI config name.
     *
     * @Default {@code "rt-server-1"}
     */
    static final CLI_CONFIG_NAME = 'rt-server-1'

    /**
     * Repository name for snapshots.
     *
     * @Default {@code "libs-snapshot-local"}
     */
    static final REPOSITORY_SNAPSHOT = 'libs-snapshot-local'

    /**
     * Repository name for releases.
     *
     * @Default {@code "libs-release-local"}
     */
    static final REPOSITORY_RELEASE = 'libs-release-local'

    /**
     * Artifactory URL.
     *
     * @Example {@code https://gizaartifactory.jfrog.io/gizaartifactory}
     */
    String url

    /**
     * Artifactory username/password credential id defined on Jenkins.
     */
    String usernamePasswordCredential

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def jfrog = new JFrogArtifactory(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    JFrogArtifactory(steps) {
        this.steps = steps
    }

    /**
     * Initialize npm registry properties.
     *
     * @Note The below parameters are supported keys of the {@code args} Map.
     *
     * @param   url                          the artifactory URL. For example {@code "https://gizaartifactory.jfrog.io/gizaartifactory"}.
     * @param   usernamePasswordCredential   Artifactory username/password credential ID
     */
    void init(Map args = [:]) {
        if (args['url']) {
            this.url = args['url']
        }
        if (args['usernamePasswordCredential']) {
            this.usernamePasswordCredential = args['usernamePasswordCredential']
        }

        if (this.steps && this.url && this.usernamePasswordCredential) {
            // prepare JFrog CLI configurations
            this.steps.withCredentials([
                this.steps.usernamePassword(
                    credentialsId: this.usernamePasswordCredential,
                    passwordVariable: 'PASSWORD',
                    usernameVariable: 'USERNAME'
                )
            ]) {
                this.steps.sh "jfrog rt config ${CLI_CONFIG_NAME}" +
                              " --url=${this.url}" +
                              " --user=\${USERNAME}" +
                              " --password=\${PASSWORD}"
            }
        }
    }

    /**
     * Get detail information of an artifact
     *
     * @Note This method is implemented with jFrog CLI.
     *
     * <p><strong>Example output:</strong><ul>
     * <li>{@code path} - {@code "libs-snapshot-local/com/project/zowe/0.9.0-SNAPSHOT/zowe-0.9.0-20180918.163158-38.pax"}</li>
     * <li>{@code build.name} - {@code "zowe-install-packaging :: master"}</li>
     * <li>{@code build.number} - {@code "38"}</li>
     * <li>{@code build.timestamp} - {@code "1537287202277"}</li>
     * </ul></p>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   pattern            path pattern to find the artifact. For example: {@code "lib-snapshot-local/path/to/artifacts/*.zip"}
     * @param   build-name         limit the search within this build name. Optional.
     * @param   build-number       limit the search within this build number. Optional.
     * @return                     a Map with the artifact information. Must include {@code path} key.
     * @throws ArtifactException   Cannot find or find more than one artifacts.
     */
    Map getArtifact(Map args = [:]) throws InvalidArgumentException, ArtifactException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!url) {
            throw new InvalidArgumentException('url')
        }
        if (!usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        Map result = [:]

        def searchOptions = ""
        def searchOptionText = ""
        if (args['build-name']) {
            // limit to build
            if (args['build-number']) {
                searchOptions = "--build=\"${args['build-name'].replace('/', ' :: ')}/${args['build-number']}\""
                searchOptionText = "in build ${args['build-name']}/${args['build-number']}"
            } else {
                searchOptions = "--build=\"${args['build-name'].replace('/', ' :: ')}\""
                searchOptionText = "in build ${args['build-name']}"
            }
        }
        this.steps.echo "Searching artifact \"${args['pattern']}\"${searchOptionText} ..."

        def resultText = this.steps.sh(
            script: "jfrog rt search ${searchOptions} \"${args['pattern']}\"",
            returnStdout: true
        ).trim()
        // this.steps.echo "Raw search result:\n${resultText}"
        /*
        Example result:
        [
          {
            "path": "libs-snapshot-local/com/project/zowe/0.9.0-SNAPSHOT/zowe-0.9.0-20180918.163158-38.pax",
            "props": {
              "build.name": "zowe-install-packaging :: master",
              "build.number": "38",
              "build.parentName": "zlux",
              "build.parentNumber": "570",
              "build.timestamp": "1537287202277"
            }
          }
        ]
        */
        def resultJson = this.steps.readJSON text: resultText

        // validate result size
        def resultSize = resultJson.size()
        if (resultSize < 1) {
            throw new ArtifactException("Cannot find artifact \"${args['pattern']}\"${searchOptionText}")
        } else if (resultSize > 1) {
            throw new ArtifactException("Found more than one artifact (${resultSize}) of \"${args['pattern']}\"${searchOptionText}")
        }

        // fetch the first artifact
        def artifactInfo = resultJson.first()

        // validate build info
        if (!artifactInfo || !artifactInfo.path) {
            throw new ArtifactException("Failed to find artifact information (path).")
        }
        result['path'] = artifactInfo.path

        // append artifact properties
        artifactInfo.props.each { prop ->
            // think this should be a bug
            // readJSON returns value as net.sf.json.JSONArray
            // this step is a workaround
            def val = prop.value
            if (val.getClass().toString().endsWith('JSONArray')) {
                val = val.get(0)
            }
            result[prop.key] = val
        }

        String readable = "Found artifact:\n"
        result.each { k, v ->
            readable += "- ${k} = ${v}\n"
        }
        this.steps.echo readable

        return result
    }

    /**
     * Get detail information of an artifact.
     *
     * @see #getArtifact(Map)
     */
    Map getArtifact(String pattern, String buildName = '', String buildNumber = '') {
        return getArtifact([
            'pattern'      : pattern,
            'build-name'   : buildName,
            'build-number' : buildNumber,
        ])
    }

    /**
     * Download artifacts
     *
     * @Note This method is implemented with jFrog CLI. The reason is sortBy in download spec is not
     *       supported by Jenkins Artifactory plugin.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @Example Download 5 artifacts to local:
     * <pre>
     *     jfrog.download(
     *         spec        : 'path/to/my/download/spec.json',
     *         expected    : 5
     *     )
     * </pre>
     * <p>Or specify download spec in parameter</p>
     * <pre>
     *     jfrog.download(
     *         specContent : '[{"file": "lib-snapshot-local/path/to/file.zip"}]',
     *         expected    : 1
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   spec            jfrog cli download specification file. optional.
     * @param   specContent     jfrog cli download specification content text.
     *                          Will be ignored if 'spec' is defined, will be .
     *                          required if 'spec' is not defined.
     * @param   expected        if we need to check download count. optional.
     */
    void download(Map args = [:]) throws InvalidArgumentException, ArtifactException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!args['spec'] && !args['specContent']) {
            throw new InvalidArgumentException('spec')
        }

        def tmpFile = ".tmp-down-artifact-spec-${Utils.getTimestamp()}.json"
        def specFile = args.containsKey('spec') ? args['spec'] : tmpFile
        if (args.containsKey('specContent')) {
            this.steps.writeFile encoding: 'UTF-8', file: tmpFile, text: args['specContent']
        }
        def expectedArtifacts = args.containsKey('expected') ? args['expected'] : -1

        // download
        def downloadResult = this.steps.sh(
            script: "jfrog rt dl --spec=\"${specFile}\"",
            returnStdout: true
        ).trim()

        def downloadResultObject = this.steps.readJSON(text: downloadResult)
        this.steps.echo "Artifact download result:\n" +
            "- status  : ${downloadResultObject['status']}\n" +
            "- success : ${downloadResultObject['totals']['success']}\n" +
            "- failure : ${downloadResultObject['totals']['failure']}"

        // validate download result
        if (downloadResultObject['status'] != 'success' || downloadResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact downloading has failure(s) or not successful.")

            if (expectedArtifacts > 0) {
                if (downloadResultObject['totals']['success'] != expectedArtifacts) {
                    throw new ArtifactException("Expected ${expectedArtifacts} artifact(s) to be downloaded but only got ${downloadResultObject['totals']['success']}.")
                }
            }
        }

        // remove spec file if it's temporary
        if (this.steps.fileExists(tmpFile)) {
            this.steps.sh("rm ${tmpFile}")
        }

        this.steps.echo "Artifact downloading is successful."
    }

    /**
     * Upload an artifact.
     *
     * <p><strong>Requires these environment variables:</strong><ul>
     * <li>- JOB_NAME</li>
     * <li>- BUILD_NUMBER</li>
     * </ul></p>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param  pattern           pattern to find local artifact(s)
     * @param  target            target path on remote Artifactory
     */
    void uploadWithCli(Map args = [:]) {
        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!args['pattern']) {
            throw new InvalidArgumentException('pattern')
        }
        if (!args['target']) {
            throw new InvalidArgumentException('target')
        }

        def env = this.steps.env
        def buildName = env.JOB_NAME.replace('/', ' :: ')
        this.steps.echo "Uploading artifact \"${args['pattern']}\" to \"${args['target']}\"\n" +
            "- Build name   : ${buildName}" +
            "- Build number : ${env.BUILD_NUMBER}"

        // prepare build info
        this.steps.sh "jfrog rt bc '${buildName}' ${env.BUILD_NUMBER}"
        // attach git information to build info if exists
        if (this.steps.fileExists('.git/HEAD')) {
            this.steps.sh "jfrog rt bag '${buildName}' ${env.BUILD_NUMBER} ."
        }

        // upload and attach with build info
        def uploadResult = this.steps.sh(
            script: "jfrog rt u \"${args['pattern']}\" \"${args['target']}\"" +
                    " --build-name=\"${buildName}\"" +
                    " --build-number=${env.BUILD_NUMBER}" +
                    " --flat",
            returnStdout: true
        ).trim()

        def uploadResultObject = readJSON(text: uploadResult)
        this.steps.echo "Artifact upload result:\n" +
            "- status  : ${uploadResultObject['status']}\n" +
            "- success : ${uploadResultObject['totals']['success']}\n" +
            "- failure : ${uploadResultObject['totals']['failure']}"

        // validate upload result
        if (uploadResultObject['status'] != 'success' ||
            uploadResultObject['totals']['success'] != 1 || uploadResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact uploading has failure(s) or not successful.")
        }

        // add environment variables to build info
        // sh "jfrog rt bce '${buildName}' ${env.BUILD_NUMBER}"
        // publish build info
        this.steps.sh "jfrog rt bp '${buildName}' ${env.BUILD_NUMBER} --build-url=${env.BUILD_URL}"

        this.steps.echo "Artifact uploading is successful."
    }

    /**
     * Upload an artifact
     *
     * <p>You can choose one of three ways to upload:<ul>
     * <li>- specify {@code spec} which pointing to a file</li>
     * <li>- specify {@code specContent} which is upload specification text</li>
     * <li>- specify {@code pattern} and {@code target}, these information will be rendered into upload specification</li>
     * </ul></p>
     *
     * @Example Upload {@code my-project.zip} to artifactory {@coode lib-snapshot-local} repository.
     * <pre>
     *     jfrog.upload(
     *         pattern : 'build/result/of/my-project.zip',
     *         target  : 'lib-snapshot-local/org/zowe/my-project/0.1.2/my-project-0.1.2-snapshot.zip'
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param  spec             jfrog cli upload specification file. optional.
     * @param  specContent      jfrog cli upload specification content text.
     * @param  pattern          pattern to find local artifact(s)
     * @param  target           target path on remote Artifactory
     * @param  properties       a map of extra properties we want to add to the artifact
     */
    void upload(Map args = [:]) {
        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!url) {
            throw new InvalidArgumentException('url')
        }
        if (!usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        def spec = ''
        if (args.containsKey('spec')) {
            spec = this.steps.readFile(encoding: 'UTF-8', file: args['spec'])
        } else if (args.containsKey('specContent')) {
            spec = args['specContent']
        } else if (args.containsKey('pattern') && args.containsKey('target')) {
            def extraProperties = ''
            if (args.containsKey('properties')) {
                // convert params to required format
                // NOTE: because of the way of passing properties, all property values
                //       are converted to string
                extraProperties = args['properties'].collect {
                    k, v -> k + '=' + URLEncoder.encode("${v}", 'UTF-8')
                }.join(';')
            }
            spec = "{\n" +
                   "  \"files\": [\n" +
                   "    {\n" +
                   "      \"pattern\": \"${args['pattern']}\",\n" +
                   "      \"target\": \"${args['target']}\",\n" +
                   "      \"props\": \"${extraProperties}\"\n" +
                   "    }\n" +
                   " ]\n" +
                   "}"
        } else {
            throw new InvalidArgumentException('spec')
        }

        def server = this.steps.Artifactory.newServer(
            'url'              : url,
            'credentialsId'    : usernamePasswordCredential
        )
        def buildInfo = server.upload(spec: spec)
        server.publishBuildInfo buildInfo

        this.steps.echo "Artifact uploading is successful."
    }

    /**
     * Upload an artifact.
     *
     * @see #upload(Map)
     */
    void upload(String pattern, String target, Map properties = [:]) {
        this.upload(pattern: pattern, target: target, properties: properties)
    }

    /**
     * Promote artifact.
     *
     * <p><strong>Requires these environment variables:</strong><ul>
     * <li>- JOB_NAME</li>
     * <li>- BUILD_NUMBER</li>
     * </ul></p>
     *
     * @Example Promote {@code my-project-0.1.2-snapshot.zip} as formal release of v0.1.2:
     * <pre>
     *     String result = jfrog.promote(
     *         source     : 'lib-snapshot-local/org/zowe/my-project/0.1.2/my-project-0.1.2-snapshot.zip',
     *         targetPath : 'lib-release-local/org/zowe/my-project/0.1.2/',
     *         targetName : 'my-project-0.1.2.zip'
     *     )
     *     assert result == 'lib-release-local/org/zowe/my-project/0.1.2/my-project-0.1.2.zip'
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param  source            information of the artifact will be promoted.
     * @param  targetPath        target path on remote Artifactory
     * @param  targetName        target artifact name. Optional, default to original name.
     * @return                   full path to the promoted artifact
     */
    String promote(Map args = [:]) throws InvalidArgumentException, ArtifactException {
        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!url) {
            throw new InvalidArgumentException('url')
        }
        if (!usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }
        if (!args.containsKey('source')) {
            throw new InvalidArgumentException('source')
        }
        if (!args.containsKey('targetPath')) {
            throw new InvalidArgumentException('targetPath')
        }

        // get soruce artifact information if not present
        Map source
        if (args['source'] instanceof Map) {
            source = args['source']
        } else if (args['source'] instanceof String) {
            source = this.getArtifact(args['source'])
        } else {
            throw new InvalidArgumentException('source')
        }

        if (!source.containsKey('path')) {
            throw new InvalidArgumentException('source', 'path property is missing from source artifact information.')
        }

        // sanitize targetPath
        def targetPath = args['targetPath']
        if (targetPath.endsWith('/')) {
            targetPath = targetPath.substring(0, targetPath.length() - 1)
        }

        // extract file name if not provided
        def targetName
        if (args.containsKey('targetName') && args['targetName']) {
            targetName = args['targetName']
        } else {
            def sourceFilenameTrunks = source['path'].split('/')
            if (sourceFilenameTrunks.size() < 1) {
                throw new ArtifactException("Invalid artifact: ${source['path']}")
            }
            targetName = sourceFilenameTrunks[-1]
        }

        def env = this.steps.env
        def buildTimestamp = source.containsKey('build.timestamp') ? source['build.timestamp'] : ''
        def buildName      = source.containsKey('build.name') ? source['build.name'] : ''
        def buildNumber    = source.containsKey('build.number') ? source['build.number'] : ''

        def targetFullPath = "${targetPath}/${targetName}"

        // variables prepared, ready to promote
        this.steps.echo "Promoting artifact: ${source['path']}\n" +
                        "- to              : ${targetFullPath}\n" +
                        "- build name      : ${buildName}\n" +
                        "- build number    : ${buildNumber}\n" +
                        "- build timestamp : ${buildTimestamp}\n"

        // promote (copy) artifact
        def promoteResult = this.steps.sh(
            script: "jfrog rt copy --flat \"${source.path}\" \"${targetFullPath}\"",
            returnStdout: true
        ).trim()

        // validate result
        def promoteResultObject = this.steps.readJSON(text: promoteResult)
        this.steps.echo "Artifact promoting result:\n" +
            "- status  : ${promoteResultObject['status']}\n" +
            "- success : ${promoteResultObject['totals']['success']}\n" +
            "- failure : ${promoteResultObject['totals']['failure']}"
        if (promoteResultObject['status'] != 'success' ||
            promoteResultObject['totals']['success'] != 1 || promoteResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact is not promoted successfully.")
        }

        // prepare artifact property
        def props = []
        def currentBuildName = env.JOB_NAME.replace('/', ' :: ')
        props << "build.name=${currentBuildName}"
        props << "build.number=${env.BUILD_NUMBER}"
        if (buildName) {
            props << "build.parentName=${buildName}"
        }
        if (buildNumber) {
            props << "build.parentNumber=${buildNumber}"
        }
        if (buildTimestamp) {
            props << "build.timestamp=${buildTimestamp}"
        }

        // update artifact property
        this.steps.echo "Updating artifact properties:\n${props.join("\n")}"

        def setPropsResult = this.steps.sh(
            script: "jfrog rt set-props \"${targetFullPath}\" \"" + props.join(';') + "\"",
            returnStdout: true
        ).trim()
        def setPropsResultObject = this.steps.readJSON(text: setPropsResult)
        this.steps.echo "Artifact promoting result:\n" +
            "- status  : ${setPropsResultObject['status']}\n" +
            "- success : ${setPropsResultObject['totals']['success']}\n" +
            "- failure : ${setPropsResultObject['totals']['failure']}"
        if (setPropsResultObject['status'] != 'success' ||
            setPropsResultObject['totals']['success'] != 1 || setPropsResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact property is not updated successfully.")
        }

        return targetFullPath
    }

    /**
     * Promote artifact.
     *
     * @see #promote(Map)
     */
    String promote(String source, String targetPath, String targetName = '') {
        return this.promote([source: source, targetPath: targetPath, targetName: targetName])
    }
}
