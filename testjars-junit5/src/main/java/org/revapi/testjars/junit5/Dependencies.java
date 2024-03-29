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

import org.revapi.testjars.DependencyResolver;

/**
 * Specifies the dependencies of the compiled sources.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Repeatable(AllDependencies.class)
public @interface Dependencies {
    /**
     * The default dependency resolver merely looks for other {@code CompiledJar}s in the test by their name.
     */
    Class<? extends DependencyResolver> resolver() default AnnotatedDependencyResolver.class;

    /**
     * The list of dependency identifiers understandable by the {@link #resolver() resolver}.
     */
    String[] value();
}
