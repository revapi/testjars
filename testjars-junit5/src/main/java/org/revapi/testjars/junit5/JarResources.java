/*
 * Copyright 2018-2021 Lukas Krejci
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

/**
 * Similar to {@link JarSources} but specifies the locations of the resources to be put into the compiled jar along with
 * the compiled classes.
 *
 * <p>
 * This annotation is repeatable in case the resources are scattered across multiple locations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Repeatable(AllJarResources.class)
public @interface JarResources {

    /**
     * Akin to {@link JarSources#root()}, specifies the root of the resources to be found on the current classpath.
     */
    String root() default "";

    /**
     * Akin to {@link JarSources#fileRoot()}, specifies the root of the resources to be found on the filesystem.
     */
    String fileRoot() default "";

    /**
     * Akin to {@link JarSources#sources()} specifies the locations of the resources relative to the {@link #root()}.
     */
    String[] resources() default {};

    /**
     * Akin to {@link JarSources#fileSources()} specifies the locations of the resources relative to the
     * {@link #fileRoot()}.
     */
    String[] fileResources() default {};
}
