/*
 * Copyright 2018-2020 Lukas Krejci
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
package org.revapi.testjars;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerManagerTest {
    private final CompilerManager compilerManager = new CompilerManager();

    @AfterEach
    void tearDown() {
        compilerManager.cleanUp();
    }

    @Test
    void shouldBuildJarFromClassPath() throws Exception {
        CompiledJar output = compilerManager.createJar().classPathSources(null, "Root.java").build();
        assertTrue(output.jarFile().exists());

        JarFile jf = new JarFile(output.jarFile());
        assertNotNull(jf.getJarEntry("Root.class"));
    }

    @Test
    void shouldResolveAgainstCustomRootInClassPath() throws Exception {
        CompiledJar output = compilerManager.createJar()
                .classPathSources("/sub-directory/", "pkg/ClassInPackage.java")
                .classPathResources("/sub-directory/", "META-INF/file-in-meta-inf.txt")
                .build();

        assertTrue(output.jarFile().exists());

        JarFile jf = new JarFile(output.jarFile());

        assertNotNull(jf.getJarEntry("pkg/ClassInPackage.class"));
        assertNotNull(jf.getJarEntry("META-INF/file-in-meta-inf.txt"));
    }

    @Test
    void shouldBuildJarsFromFiles() throws Exception {
        Path root = Files.createTempDirectory("JarTest");
        Path metaInf = Files.createDirectory(root.resolve("META-INF"));
        Path pkg = Files.createDirectory(root.resolve("pkg"));

        Files.copy(getClass().getResourceAsStream("/sub-directory/pkg/ClassInPackage.java"),
                pkg.resolve("ClassInPackage.java"));
        Files.copy(getClass().getResourceAsStream("/sub-directory/META-INF/file-in-meta-inf.txt"),
                metaInf.resolve("file-in-meta-inf.txt"));

        CompiledJar output = compilerManager.createJar()
                .fileSources(root.toFile(), root.relativize(pkg.resolve("ClassInPackage.java")).toFile())
                .fileResources(root.toFile(), root.relativize(metaInf.resolve("file-in-meta-inf.txt")).toFile())
                .build();

        assertTrue(output.jarFile().exists());

        JarFile jf = new JarFile(output.jarFile());

        assertNotNull(jf.getJarEntry("pkg/ClassInPackage.class"));
        assertNotNull(jf.getJarEntry("META-INF/file-in-meta-inf.txt"));
    }

    @Test
    void shouldProvideFunctionalTypeEnvironment() throws Exception {
        CompiledJar output = compilerManager.createJar()
                .classPathSources("/sub-directory/", "pkg/ClassInPackage.java")
                .classPathResources("/sub-directory/", "META-INF/file-in-meta-inf.txt")
                .build();

        CompiledJar.Environment env = output.analyze();

        assertNotNull(env.elements().getTypeElement("pkg.ClassInPackage"));
    }

    @Test
    void shouldCompileWithDependenciesUsingResolver() throws Exception {
        CompiledJar dep = compilerManager.createJar().classPathSources("/deps/dep/", "Dep.java").build();

        compilerManager
                .createJar(id -> {
                    if ("dep".equals(id)) {
                        return singleton(dep.jarFile());
                    } else {
                        return emptySet();
                    }
                })
                .classPathSources("/deps/main/", "Main.java")
                .dependencies("dep").build();

        // cool, it's enough for us to know that the above compilation passed.
    }

    @Test
    void shouldCompileWithDependenciesUsingFiles() throws Exception {
        CompiledJar dep = compilerManager.createJar().classPathSources("/deps/dep/", "Dep.java").build();

        compilerManager.createJar()
                .classPathSources("/deps/main/", "Main.java")
                .dependencies(dep.jarFile())
                .build();

        // cool, it's enough for us to know that the above compilation passed.
    }
}
