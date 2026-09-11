/*
 * (C) Copyright 2026 Hyland (http://hyland.com/) and others.
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
 *
 * Contributors:
 *     Thibaud Arguillere (With the help of Opencode/Claude Opus for the Web UI port from a Studio project)
 */
package org.nuxeo.labs.hyland.content.intelligence.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationType;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import jakarta.inject.Inject;

/**
 * Guards the Automation surface of the plugin. Runs fully offline, no CIC credentials needed.
 * <p>
 * These tests exist because of a real regression: {@code ContentLakeGetDocumentOp} shipped with a copy/paste of
 * {@code IngestCheckDigestOp}'s ID and was never registered in {@code automation-contrib.xml}. The whole Content
 * Lake family was therefore unreachable from Automation, and simply adding the missing registration would have
 * made the server fail to start on the duplicate ID.
 *
 * @since 2025.22
 */
@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, ConfigCheckerFeature.class })
@Deploy("nuxeo-hyland-content-intelligence-connector-core")
public class TestAutomationRegistration {

    protected static final String AUTOMATION_PACKAGE = "org.nuxeo.labs.hyland.content.intelligence.automation";

    protected static final String CLASS_SUFFIX = ".class";

    @Inject
    protected AutomationService automationService;

    /**
     * Walks the compiled {@code automation} package and returns every class annotated with {@link Operation}.
     * Scanning the classpath rather than a hardcoded list is the whole point: a newly added operation is picked
     * up automatically, which is what makes these tests a guard rather than a snapshot.
     */
    protected List<Class<?>> findOperationClasses() throws Exception {

        String packagePath = AUTOMATION_PACKAGE.replace('.', '/');
        URL url = getClass().getClassLoader().getResource(packagePath);
        assertNotNull("Cannot locate " + packagePath + " on the test classpath", url);

        Path root = Paths.get(url.toURI());
        List<Class<?>> operations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            List<Path> classFiles = files.filter(p -> p.toString().endsWith(CLASS_SUFFIX)).toList();
            for (Path classFile : classFiles) {
                String relative = root.relativize(classFile).toString().replace(File.separatorChar, '.');
                String className = AUTOMATION_PACKAGE + "."
                        + relative.substring(0, relative.length() - CLASS_SUFFIX.length());
                // Skip nested and anonymous classes, they never carry @Operation.
                if (className.indexOf('$') >= 0) {
                    continue;
                }
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Operation.class)) {
                    operations.add(clazz);
                }
            }
        }

        return operations;
    }

    /**
     * Sanity check: the scanner must actually find something, otherwise the two tests below would pass vacuously.
     */
    @Test
    public void shouldFindTheOperationClasses() throws Exception {

        List<Class<?>> operations = findOperationClasses();
        assertFalse("No @Operation class found, the classpath scan is broken", operations.isEmpty());
        assertTrue("Suspiciously few @Operation classes found: " + operations.size(), operations.size() >= 35);
    }

    /**
     * Two operation classes sharing the same ID make the server fail at startup as soon as both are registered
     * ({@code AutomationComponent.registerContribution} throws "An operation is already bound to: ..."). Catching
     * it here is considerably cheaper than catching it on a deployed instance.
     */
    @Test
    public void operationIdsShouldBeUnique() throws Exception {

        Map<String, String> idToClassName = new HashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (Class<?> clazz : findOperationClasses()) {
            String id = clazz.getAnnotation(Operation.class).id();
            String previous = idToClassName.put(id, clazz.getName());
            if (previous != null) {
                duplicates.add(id + " declared by " + previous + " and " + clazz.getName());
            }
        }

        assertTrue("Duplicate operation ID(s): " + duplicates, duplicates.isEmpty());
    }

    /**
     * Every {@code @Operation} class must be listed in {@code OSGI-INF/automation-contrib.xml}. An unregistered
     * operation compiles, is covered by no test, and simply does not exist at runtime.
     */
    @Test
    public void everyOperationShouldBeRegistered() throws Exception {

        List<String> problems = new ArrayList<>();

        for (Class<?> clazz : findOperationClasses()) {
            String id = clazz.getAnnotation(Operation.class).id();
            try {
                OperationType type = automationService.getOperation(id);
                if (!clazz.equals(type.getType())) {
                    problems.add(id + " resolves to " + type.getType().getName() + " instead of " + clazz.getName());
                }
            } catch (OperationException e) {
                problems.add(id + " (" + clazz.getName() + ") is not registered in automation-contrib.xml");
            }
        }

        assertTrue("Operation registration problem(s): " + problems, problems.isEmpty());
    }

}
