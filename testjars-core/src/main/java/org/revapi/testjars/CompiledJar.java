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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Represents the compiled jar. Provides access to the created files and also the means to analyze them.
 */
public final class CompiledJar {
    private final File jarFile;
    private final File classes;
    private final List<File> classpath;
    private final CompilerManager compiler;

    CompiledJar(File jarFile, File classes, File[] classpath, CompilerManager compiler) {
        this.jarFile = jarFile;
        this.classes = classes;
        this.classpath = classpath == null ? Collections.emptyList() : Arrays.asList(classpath);
        this.compiler = compiler;
    }

    /**
     * @return the compiled jar file
     */
    public File jarFile() {
        return jarFile;
    }

    /**
     * @return the root directory containing the compiled classes.
     */
    @Nullable
    public File classes() {
        return classes;
    }

    /**
     * The classpath this jar was compiled with.
     */
    public List<File> classpath() {
        return classpath;
    }

    /**
     * @return an environment similar to java annotation processing round environment that gives access to
     * {@link Elements} and {@link Types} instances that can be used to analyze the compiled classes.
     */
    public Environment analyze() {
        return compiler.probe(this);
    }

    public static final class Environment {
        Elements elements;
        Types types;
        ProcessingEnvironment processingEnvironment;

        public Elements elements() {
            return elements;
        }

        public Types types() {
            return types;
        }

        public ProcessingEnvironment processingEnvironment() {
            return processingEnvironment;
        }
    }
}
