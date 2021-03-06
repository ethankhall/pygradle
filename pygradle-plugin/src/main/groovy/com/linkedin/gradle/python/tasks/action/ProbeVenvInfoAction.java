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
package com.linkedin.gradle.python.tasks.action;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.linkedin.gradle.python.extension.PythonDetails;
import com.linkedin.gradle.python.wheel.AbiDetails;
import com.linkedin.gradle.python.wheel.EditablePythonAbiContainer;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ProbeVenvInfoAction {

    private static final Logger logger = Logging.getLogger(ProbeVenvInfoAction.class);

    private ProbeVenvInfoAction() {
        //This is an internal class
    }

    static void probeVenv(Project project, PythonDetails pythonDetails,
                                  EditablePythonAbiContainer editablePythonAbiContainer) {
        try {
            doProbe(project, pythonDetails, editablePythonAbiContainer);
        } catch (IOException ioe) {
            logger.info("Unable to probe venv for supported wheel details. Ignoring Venv.");
        }
    }

    private static void doProbe(Project project, PythonDetails pythonDetails,
                          EditablePythonAbiContainer editablePythonAbiContainer) throws IOException {
        InputStream wheelApiResource = ProbeVenvInfoAction.class.getClassLoader()
            .getResourceAsStream("templates/wheel-api.py");

        byte[] buffer = new byte[wheelApiResource.available()];
        wheelApiResource.read(buffer);

        File probeDir = new File(project.getBuildDir(), "prob-venv");
        probeDir.mkdirs();

        OutputStream outStream = new FileOutputStream(getPythonFileForSupportedWheels(probeDir));
        outStream.write(buffer);

        File supportedAbiFormatsFile = getSupportedAbiFormatsFile(probeDir, pythonDetails);
        project.exec(execSpec -> {
            execSpec.commandLine(pythonDetails.getVirtualEnvInterpreter());
            execSpec.args(getPythonFileForSupportedWheels(probeDir));
            execSpec.args(supportedAbiFormatsFile.getAbsolutePath());
        });

        JsonArray array = Json.parse(new FileReader(supportedAbiFormatsFile)).asArray();
        for (JsonValue jsonValue : array) {
            JsonObject entry = jsonValue.asObject();
            String pythonTag = entry.get("pythonTag").asString();
            String abiTag = entry.get("abiTag").asString();
            String platformTag = entry.get("platformTag").asString();

            AbiDetails triple = new AbiDetails(pythonDetails.getVirtualEnvInterpreter(),
                pythonTag, abiTag, platformTag);
            editablePythonAbiContainer.addSupportedAbi(triple);
        }
    }

    private static File getPythonFileForSupportedWheels(File tempDir) {
        return new File(tempDir, "wheel-api.py");
    }

    private static File getSupportedAbiFormatsFile(File buildDir, PythonDetails pythonDetails) {
        return new File(buildDir,
            "wheel-abi-result" + pythonDetails.getPythonVersion().getPythonMajorMinor() + ".json");
    }
}
