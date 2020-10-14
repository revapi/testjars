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
package org.revapi.testjars.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import javax.lang.model.element.TypeElement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.revapi.testjars.CompiledJar;

@ExtendWith(CompiledJarExtension.class)
class ExtensionTest {

    @JarSources(root = "/", sources = "TestClass.java")
    @JarResources(root = "/", resources = "resources/res1.txt")
    @JarResources(root = "/", resources = "resources/res2.txt")
    private CompiledJar jar;

    @JarSources(root = "/", sources = {"TestClass.java", "TestAnotherClass.java"})
    private CompiledJar.Environment env1;

    @JarSources(root = "/", sources = "TestClass.java")
    @JarSources(root = "/", sources = "TestAnotherClass.java")
    private CompiledJar.Environment env2;

    @JarSources(name = "base", root = "/", sources = "TestClass.java")
    private CompiledJar baseJar;

    @JarSources(root = "/", sources = "DependentTestClass.java")
    @Dependencies("base")
    private CompiledJar depByNameJar;

    @Test
    void testJar() {
        assertNotNull(jar.jarFile());

        assertNotNull(jar.classes());
        assertTrue(new File(jar.classes(), "TestClass.class").exists());

        TypeElement testClass = jar.analyze().elements().getTypeElement("TestClass");
        assertNotNull(testClass);
    }

    @Test
    void testEnv1() throws Exception {
        testEnv(env1);
    }

    @Test
    void testEnv2() throws Exception {
        testEnv(env2);
    }

    @Test
    void testDependentJars() throws Exception {
        assertEquals(1, depByNameJar.classpath().size());
        assertEquals(baseJar.jarFile(), depByNameJar.classpath().get(0));
    }

    private static void testEnv(CompiledJar.Environment env) throws Exception {
        assertNotNull(env);

        TypeElement testClass = env.elements().getTypeElement("TestClass");
        TypeElement testAnotherClass = env.elements().getTypeElement("TestAnotherClass");
        assertNotNull(testClass);
        assertNotNull(testAnotherClass);

    }
}
