/*
 * Copyright 2018 Lukas Krejci
 * and other contributors as indicated by the @author tags.
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
package org.revapi.testjars.junit5;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.revapi.testjars.CompiledJar;
import org.revapi.testjars.CompilerManager;

/**
 * A JUnit5 extension to dynamically compile jar files from specified sources and make them available for the tests
 * to access and analyze.
 * <p>
 * <p>Each field with type {@link CompiledJar} is initialized to an instance containing the compilation results
 * of the sources and resources specified by the {@link JarSources} and {@link JarResources} annotations on the field.
 * The field can alternatively also have type {@link CompiledJar.Environment}.
 */
public final class CompiledJarExtension implements TestInstancePostProcessor, AfterAllCallback {
    private final CompilerManager compilerManager = new CompilerManager();

    @Override
    public void afterAll(ExtensionContext context) {
        compilerManager.cleanUp();
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        for (Field f : testClass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                boolean isEnv = CompiledJar.Environment.class.isAssignableFrom(f.getType());
                if (!isEnv && !CompiledJar.class.isAssignableFrom(f.getType())) {
                    continue;
                }

                JarSources[] sources;

                AllJarSources allSources = f.getAnnotation(AllJarSources.class);
                if (allSources != null) {
                    sources = allSources.value();
                } else {
                    JarSources singleSources = f.getAnnotation(JarSources.class);
                    if (singleSources == null) {
                        continue;
                    }
                    sources = new JarSources[]{singleSources};
                }

                CompilerManager.JarBuilder bld = compilerManager.createJar();

                for (JarSources src : sources) {
                    if (!src.root().isEmpty() && src.sources().length != 0) {
                        bld.classPathSources(src.root(), src.sources());
                    }

                    if (!src.fileRoot().isEmpty() && src.fileSources().length != 0) {
                        bld.fileSources(new File(src.fileRoot()), Stream.of(src.fileSources())
                                .map(File::new).toArray(File[]::new));
                    }
                }

                JarResources[] resources = null;

                AllJarResources allResources = f.getAnnotation(AllJarResources.class);
                if (allResources != null) {
                    resources = allResources.value();
                } else {
                    JarResources singleResources = f.getAnnotation(JarResources.class);
                    if (singleResources != null) {
                        resources = new JarResources[]{singleResources};
                    }
                }

                if (resources != null) {
                    for (JarResources rsc : resources) {
                        if (!rsc.root().isEmpty() && rsc.resources().length != 0) {
                            bld.classPathResources(rsc.root(), rsc.resources());
                        }

                        if (!rsc.fileRoot().isEmpty() && rsc.fileResources().length != 0) {
                            bld.fileResources(new File(rsc.fileRoot()), Stream.of(rsc.fileResources())
                                    .map(File::new).toArray(File[]::new));
                        }
                    }
                }

                CompiledJar compiledJar = bld.build();

                f.setAccessible(true);
                if (isEnv) {
                    f.set(testInstance, compiledJar.analyze());
                } else {
                    f.set(testInstance, compiledJar);
                }
            }
        }
    }
}
