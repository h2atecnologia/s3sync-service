/*
s3sync-service - Realtime S3 synchronisation tool
Copyright (c) 2020  Yevgeniy Valeyev

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.golang
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

version = "2020.1"

object GitGithubComMazayS3syncServiceGit : GitVcsRoot({
    name = "git@github.com:mazay/s3sync-service.git"
    url = "git@github.com:mazay/s3sync-service.git"
    branchSpec = "+:refs/heads/*"
    authMethod = uploadedKey {
        userName = "git"
        uploadedKey = "teamcity_github_s3sync_service"
    }
})

project {

    vcsRoot(GitGithubComMazayS3syncServiceGit)

    buildType(UnitTesting)
    buildType(DockerBuild)
    buildType(Build)
    buildType(Release)

    features {
        feature {
            id = "PROJECT_EXT_2"
            type = "IssueTracker"
            param("secure:password", "")
            param("name", "mazay/s3sync-service")
            param("pattern", """#(\d+)""")
            param("authType", "anonymous")
            param("repository", "https://github.com/mazay/s3sync-service")
            param("type", "GithubIssues")
            param("secure:accessToken", "")
            param("username", "")
        }
    }
}

object UnitTesting : BuildType({
    name = "Unit Testing"

    allowExternalStatus = true

    params {
        param("teamcity.build.default.checkoutDir", "src/s3sync-service")
        param("env.GOFLAGS", "-json")
        param("env.GOPATH", "/opt/buildagent/work")
        password(
                "s3sync-service.github.token",
                "credentialsJSON:38d0338a-0796-4eaa-a625-d9b720d9af17",
                label = "Github Token",
                display = ParameterDisplay.HIDDEN,
                readOnly = true
        )
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            workingDir = "src"
            name = "Go get dependencies"
            scriptContent = "go mod vendor"
            formatStderrAsError = true
        }
        script {
            workingDir = "src"
            name = "Linter check"
            scriptContent = """
                #!/usr/bin/env bash

                ${'$'}{GOBIN}/golint -set_exit_status .
            """.trimIndent()
            formatStderrAsError = true
        }
        script {
            workingDir = "src"
            name = "Go run unit tests"
            scriptContent = "go test"
            formatStderrAsError = true
        }
    }


    /* triggers {
        vcs {
        }
    } */

    features {
        pullRequests {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            provider = github {
                authType = token {
                    token = "credentialsJSON:8c15f79d-8a9d-4ab0-9057-7f7bc00883c3"
                }
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
            }
        }
        golang {
            testFormat = "json"
        }
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:8c15f79d-8a9d-4ab0-9057-7f7bc00883c3"
                }
            }
        }
    }
})

object DockerBuild : BuildType({
    name = "Docker build"

    allowExternalStatus = true

    params {
        param("teamcity.build.default.checkoutDir", "src/s3sync-service")
        param("env.RELEASE_VERSION", "%teamcity.build.branch%")
        password(
                "s3sync-service.github.token",
                "credentialsJSON:38d0338a-0796-4eaa-a625-d9b720d9af17",
                label = "Github Token",
                display = ParameterDisplay.HIDDEN,
                readOnly = true
        )
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Docker multi-arch"
            scriptContent = """
                #!/usr/bin/env bash

                if [ "${'$'}{RELEASE_VERSION}" = "master" ]; then
                    RELEASE_VERSION="latest"
                fi

                make docker-multi-arch
            """.trimIndent()
            formatStderrAsError = true
        }
    }


    /* triggers {
        vcs {
        }
    } */

    dependencies {
        snapshot(UnitTesting){
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    features {
        dockerSupport {
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_5"
            }
        }
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:8c15f79d-8a9d-4ab0-9057-7f7bc00883c3"
                }
            }
        }
    }
})

object Build : BuildType({
    name = "Build"

    artifactRules = "s3sync-service-*"

    params {
        param("teamcity.build.default.checkoutDir", "src/s3sync-service")
        param("env.RELEASE_VERSION", "%teamcity.build.branch%")
        param("env.DEBIAN_FRONTEND", "noninteractive")
        param("env.GOPATH", "/opt/buildagent/work")
        password(
          "s3sync-service.github.token",
          "credentialsJSON:38d0338a-0796-4eaa-a625-d9b720d9af17",
          label = "Github Token",
          display = ParameterDisplay.HIDDEN,
          readOnly = true
        )
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            workingDir = "src"
            name = "Go get dependencies"
            scriptContent = "go mod vendor"
            formatStderrAsError = true
        }
        script {
            name = "Go build"
            scriptContent = "make build-all"
            formatStderrAsError = true
        }
    }

    dependencies {
        snapshot(UnitTesting){
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})

object Release : BuildType({
    name = "Release"

    params {
        param("teamcity.build.default.checkoutDir", "src/s3sync-service")
        param("env.RELEASE_VERSION", "")
        /* override RELEASE_VERSION in Build and DockerBuild */
        param("reverse.dep.S3syncService_Build.env.RELEASE_VERSION", "")
        param("reverse.dep.S3syncService_DockerBuild.env.RELEASE_VERSION", "")
        /* end override */
        param("env.RELEASE_CHANGELOG", "")
        param("env.GOPATH", "/opt/buildagent/work")
        checkbox("env.DRAFT_RELEASE", "true",
                checked = "true", unchecked = "false")
        checkbox("env.PRE_RELEASE", "true",
                checked = "true", unchecked = "false")
        password(
                "env.GITHUB_TOKEN",
                "credentialsJSON:38d0338a-0796-4eaa-a625-d9b720d9af17",
                label = "Github Token",
                display = ParameterDisplay.HIDDEN,
                readOnly = true
        )
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Release"
            scriptContent = """
                #!/usr/bin/env bash

                ADDITIONAL_KEYS="-"
                ATTACHMENTS=""

                cat >release.md <<EOF
                ${'$'}{RELEASE_VERSION}

                ${'$'}{RELEASE_CHANGELOG}

                **image:** \`zmazay/s3sync-service:${'$'}{RELEASE_VERSION}\`
                EOF

                if [[ ${'$'}{DRAFT_RELEASE} == true ]]
                then
                  ADDITIONAL_KEYS="${'$'}{ADDITIONAL_KEYS}d"
                fi

                if [[ ${'$'}{PRE_RELEASE} == true ]]
                then
                  ADDITIONAL_KEYS="${'$'}{ADDITIONAL_KEYS}p"
                fi

                if [[ ${'$'}{ADDITIONAL_KEYS} == "-" ]]
                then
                  ADDITIONAL_KEYS=""
                fi

                for artifact in s3sync-service-*
                do
                  ATTACHMENTS="${'$'}{ATTACHMENTS} -a ${'$'}{artifact}"
                done

                hub release create ${'$'}{ADDITIONAL_KEYS} -F release.md ${'$'}{RELEASE_VERSION} ${'$'}{ATTACHMENTS}
            """.trimIndent()
            formatStderrAsError = true
        }
    }

    dependencies {
        snapshot(UnitTesting){
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(DockerBuild){
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        dependency(Build) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }
            artifacts {
                artifactRules = "s3sync-service-*"
            }
        }
    }

    features {
        dockerSupport {
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_5"
            }
        }
    }
})
