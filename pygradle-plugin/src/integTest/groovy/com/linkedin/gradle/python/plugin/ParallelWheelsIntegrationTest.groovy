/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.gradle.python.plugin

import com.linkedin.gradle.python.plugin.testutils.DefaultProjectLayoutRule
import com.linkedin.gradle.python.plugin.testutils.ExecUtils
import com.linkedin.gradle.python.plugin.testutils.PyGradleTestBuilder
import com.linkedin.gradle.python.util.OperatingSystem
import com.linkedin.gradle.python.util.PexFileUtil
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths

class ParallelWheelsIntegrationTest extends Specification {

    @Rule
    final DefaultProjectLayoutRule testProjectDir = new DefaultProjectLayoutRule(false)

    @IgnoreIf({ OperatingSystem.current() == OperatingSystem.WINDOWS })
    def "can build thin pex"() {
        testProjectDir
        given:
        testProjectDir.buildFile << """\
        | plugins {
        |     id 'com.linkedin.python-pex'
        | }
        | apply plugin: com.linkedin.gradle.python.plugin.WheelFirstPlugin
        | version = '1.0.0'
        | python {
        |   pex {
        |     fatPex = false
        |   }
        | }
        | ${PyGradleTestBuilder.createRepoClosure()}
        """.stripMargin().stripIndent()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('build', '--stacktrace', '--info')
            .withPluginClasspath()
            .withDebug(true)
            .build()
        println result.output

        Path deployablePath = testProjectDir.root.toPath().resolve(Paths.get('foo', 'build', 'deployable', "bin"))

        then:

        result.output.contains("BUILD SUCCESS")
        result.task(':foo:flake8').outcome == TaskOutcome.SUCCESS
        result.task(':foo:findPythonAbi').outcome == TaskOutcome.SUCCESS
        result.task(':foo:parallelWheels').outcome == TaskOutcome.SUCCESS
        result.task(':foo:installPythonRequirements').outcome == TaskOutcome.SUCCESS
        result.task(':foo:installTestRequirements').outcome == TaskOutcome.SUCCESS
        result.task(':foo:createVirtualEnvironment').outcome == TaskOutcome.SUCCESS
        result.task(':foo:installProject').outcome == TaskOutcome.SUCCESS
        result.task(':foo:pytest').outcome == TaskOutcome.SUCCESS
        result.task(':foo:check').outcome == TaskOutcome.SUCCESS
        result.task(':foo:build').outcome == TaskOutcome.SUCCESS
        result.task(':foo:buildPex').outcome == TaskOutcome.SUCCESS

        deployablePath.resolve('hello_world').toFile().exists()
        deployablePath.resolve(PexFileUtil.createThinPexFilename('foo')).toFile().exists()

        when: "we have a pex file"
        def line = new String(deployablePath.resolve(PexFileUtil.createThinPexFilename('foo')).bytes, "UTF-8").substring(0, 100)

        then: "its shebang line is not pointing to a virtualenv"
        line.startsWith("#!") && !line.contains("venv")

        when:
        def out = ExecUtils.run(deployablePath.resolve('hello_world'))
        println out

        then:
        out.toString() == "Hello World${System.getProperty("line.separator")}".toString()
    }
}
