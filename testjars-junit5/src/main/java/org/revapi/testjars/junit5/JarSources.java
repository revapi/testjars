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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.revapi.testjars.CompiledJar;

/**
 * This annotation is used to specify the sources from which the {@link CompiledJar} should be produced. The field
 * annotated by this annotation can either have type {@link CompiledJar} or alternatively
 * {@link CompiledJar.Environment} if you are only interested in analyzing the compiled classes.
 *
 * <p>The source files can either be located on the classpath, in which case they are specified using the
 * {@link #sources()} attribute, or on the file system, which are specified using the {@link #fileSources()} attribute.
 *
 * <p>This annotation is repeatable in case the sources are scattered across multiple locations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Repeatable(AllJarSources.class)
public @interface JarSources {
    /**
     * The root path on the classpath to which all the {@link #sources()} are relative to. The compiled classes will
     * mimic the directory structure under the root. Thi path must end with a "/".
     */
    String root() default "";

    /**
     * Similar to {@link #root()} but used for the {@link #fileSources()}
     */
    String fileRoot() default "";

    /**
     * Paths under the {@link #root()} denoting the locations on the classpath of the source files to compile.
     * The paths are relative to the {@link #root()}.
     */
    String[] sources() default {};

    /**
     * Paths under the {@link #fileRoot()} ()} denoting the locations on the file system of the source files to compile.
     * The paths are relative to the {@link #fileRoot()}.
     */
    String[] fileSources() default {};
}
