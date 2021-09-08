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

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.revapi.testjars.CompiledJar;
import org.revapi.testjars.DependencyResolver;

/**
 * A helper class used by {@link CompiledJarExtension} to realize inter-jar dependencies.
 */
class AnnotatedDependencyResolver implements DependencyResolver {
    private final Map<String, CompiledJar> results;
    private final Map<String, Set<String>> depsTransitiveClosure;

    AnnotatedDependencyResolver(Map<String, CompiledJar> results, Map<String, Set<String>> depsTransitiveClosure) {
        this.results = results;
        this.depsTransitiveClosure = depsTransitiveClosure;
    }

    @Override
    public Set<File> resolve(String s) {
        HashSet<File> ret = new HashSet<>();
        CompiledJar jar = results.get(s);
        if (jar != null) {
            ret.add(jar.jarFile());
        }

        Set<String> deps = depsTransitiveClosure.get(s);
        if (deps != null) {
            for (String dep : deps) {
                jar = results.get(dep);
                if (jar != null) {
                    ret.add(jar.jarFile());
                }
            }
        }

        return ret;
    }
}
