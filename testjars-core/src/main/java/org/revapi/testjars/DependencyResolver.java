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
import java.util.Set;

/**
 * Implementations of this interface can find jar files to add to the classpath when
 * compiling the test jar.
 */
public interface DependencyResolver {

    /**
     * Resolves a dependency described by the provided identifier and all its transitive dependencies into a set oflocal
     * files that can be used when compiling the test jar.
     *
     * The implementation is responsible for managing the files (e.g. clean up, etc).
     */
    Set<File> resolve(String identifier);
}
