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
package org.revapi.testjars.junit4;

import java.io.File;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.revapi.testjars.CompiledJar;
import org.revapi.testjars.CompilerManager;

/**
 * <p>This class can be used in tests to make it easy to compile custom source code into jars and then use the Java
 * Annotation processing API to analyze the compiled classes.
 *
 * <p>Simply declare a JUnit4 rule field:<pre><code>
 *    {@literal @Rule}
 *     public Jar jar = new Jar();
 * </code></pre>
 *
 * <p>and then use it in your test methods to compile and use code:<pre><code>
 *     Jar.BuildOutput build = jar.from()
 *         .classPathSources("/", "my/package/MySource.java")
 *         .classPathResources("/", "META-INF/my-file-in-jar.txt")
 *         .build();
 *
 *     Jar.Environment env = build.analyze();
 *     TypeElement mySourceClass = env.elements().getElement("my.package.MySource");
 *     ...
 *     File jarFile = build.jarFile();
 *     Files.copy(jarFile.toPath(), Paths.get("/"));
 *     ...
 * </code></pre>
 *
 * <p>This class is a thin wrapper around {@link CompilerManager} that integrates it with JUnit4.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public class Jar implements TestRule {
    private final CompilerManager compilerManager = new CompilerManager();

    /**
     * Applies a jar rule to a test method. Don't call directly but instead let JUnit handle it.
     */
    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    compilerManager.cleanUp();
                }
            }
        };
    }

    /**
     * Calls {@link CompilerManager#createJar()}.
     */
    public CompilerManager.JarBuilder from() {
        return compilerManager.createJar();
    }

    /**
     * If you already have a compiled jar file, you can start analyzing its contents using this method.
     * <p>
     * Note that this file is not automatically deleted after a test as would a jar file built using the {@link #from()}
     * method. If you want this file to also be cleaned up, use the {@link #manage(File)} method.
     *
     * @param jarFile the jar file to analyze
     * @return object using which the classes within the jar file can be inspected.
     */
    public CompiledJar of(File jarFile) {
        return compilerManager.jarFrom(jarFile);
    }

    /**
     * Given file will be automatically cleaned up after the test.
     *
     * @param jarFile a file to delete once the test is finished.
     */
    public void manage(File jarFile) {
        compilerManager.manage(jarFile);
    }
}
