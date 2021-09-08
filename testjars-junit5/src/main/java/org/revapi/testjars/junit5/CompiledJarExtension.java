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

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.revapi.testjars.CompiledJar;
import org.revapi.testjars.CompilerManager;
import org.revapi.testjars.DependencyResolver;

/**
 * A JUnit5 extension to dynamically compile jar files from specified sources and make them available for the tests to
 * access and analyze.
 * <p>
 * <p>
 * Each field with type {@link CompiledJar} is initialized to an instance containing the compilation results of the
 * sources and resources specified by the {@link JarSources} and {@link JarResources} annotations on the field. The
 * field can alternatively also have type {@link CompiledJar.Environment}.
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
        List<Field> eligibleFields = findEligibleFields(testClass);
        Map<String, Set<String>> depsTransitiveClosure = sortByDependenciesAndReturnTransitiveClosureOfDeps(
                eligibleFields)
                        .entrySet().stream().filter(e -> getJarName(e.getKey()) != null)
                        .collect(Collectors.toMap(e -> getJarName(e.getKey()),
                                e -> e.getValue().stream().map(CompiledJarExtension::getJarName)
                                        .filter(Objects::nonNull).collect(Collectors.toSet())));

        Map<String, CompiledJar> namedResults = new HashMap<>();

        for (Field f : eligibleFields) {
            if (!Modifier.isStatic(f.getModifiers())) {
                boolean isEnv = CompiledJar.Environment.class.isAssignableFrom(f.getType());
                if (!isEnv && !CompiledJar.class.isAssignableFrom(f.getType())) {
                    continue;
                }

                JarSources[] sources = f.getAnnotationsByType(JarSources.class);
                if (sources.length == 0) {
                    continue;
                }

                Dependencies[] deps = f.getAnnotationsByType(Dependencies.class);
                Map<String, DependencyResolver> resolvers = new HashMap<>();
                for (Dependencies d : deps) {
                    DependencyResolver dr;
                    if (AnnotatedDependencyResolver.class.equals(d.resolver())) {
                        dr = new AnnotatedDependencyResolver(namedResults, depsTransitiveClosure);
                    } else {
                        dr = d.resolver().newInstance();
                    }

                    for (String id : d.value()) {
                        resolvers.put(id, dr);
                    }
                }

                CompilerManager.JarBuilder bld = compilerManager
                        .createJar(id -> resolvers.getOrDefault(id, CluelessDependencyResolver.INSTANCE).resolve(id));

                String name = null;
                for (JarSources src : sources) {
                    if (!src.root().isEmpty() && src.sources().length != 0) {
                        bld.classPathSources(src.root(), src.sources());
                    }

                    if (!src.fileRoot().isEmpty() && src.fileSources().length != 0) {
                        bld.fileSources(new File(src.fileRoot()),
                                Stream.of(src.fileSources()).map(File::new).toArray(File[]::new));
                    }

                    if (resolvers.size() == 1) {
                        bld.dependencies(resolvers.keySet().iterator().next());
                    } else if (resolvers.size() > 1) {
                        List<String> ds = new ArrayList<>(resolvers.keySet());
                        String first = ds.remove(0);
                        String[] rest = ds.toArray(new String[0]);

                        bld.dependencies(first, rest);
                    }

                    if (src.name().length() > 0) {
                        name = src.name();
                    }
                }

                JarResources[] resources = f.getAnnotationsByType(JarResources.class);
                for (JarResources rsc : resources) {
                    if (!rsc.root().isEmpty() && rsc.resources().length != 0) {
                        bld.classPathResources(rsc.root(), rsc.resources());
                    }

                    if (!rsc.fileRoot().isEmpty() && rsc.fileResources().length != 0) {
                        bld.fileResources(new File(rsc.fileRoot()),
                                Stream.of(rsc.fileResources()).map(File::new).toArray(File[]::new));
                    }
                }

                CompiledJar compiledJar = bld.build();

                if (name != null) {
                    namedResults.put(name, compiledJar);
                }

                f.setAccessible(true);
                if (isEnv) {
                    f.set(testInstance, compiledJar.analyze());
                } else {
                    f.set(testInstance, compiledJar);
                }
            }
        }
    }

    private static Map<Field, Set<Field>> sortByDependenciesAndReturnTransitiveClosureOfDeps(List<Field> jarFields) {
        Map<Field, Set<Field>> deps = determineFieldDependencies(jarFields);

        transitiveClosure(deps);
        jarFields.sort((a, b) -> {
            if (deps.getOrDefault(a, emptySet()).contains(b)) {
                return 1;
            } else if (deps.getOrDefault(b, emptySet()).contains(a)) {
                return -1;
            } else {
                return 0;
            }
        });

        return deps;
    }

    private static void transitiveClosure(Map<Field, Set<Field>> firstOrderDeps) {
        for (Field f : firstOrderDeps.keySet()) {
            transitiveClosure(f, new HashSet<>(), firstOrderDeps);
        }
    }

    private static void transitiveClosure(Field toUpdate, Set<Field> processed, Map<Field, Set<Field>> allDeps) {
        if (processed.contains(toUpdate)) {
            throw new IllegalArgumentException("Cyclic dependencies.");
        }

        processed.add(toUpdate);

        Set<Field> deepDeps = new HashSet<>();
        for (Field d : allDeps.getOrDefault(toUpdate, emptySet())) {
            transitiveClosure(d, processed, allDeps);
            deepDeps.addAll(allDeps.get(d));
        }
        allDeps.put(toUpdate, deepDeps);
    }

    private static String getJarName(Field f) {
        String name = null;
        for (JarSources s : f.getAnnotationsByType(JarSources.class)) {
            if (!s.name().isEmpty()) {
                if (name != null) {
                    throw new IllegalArgumentException();
                } else {
                    name = s.name();
                }
            }
        }

        return name;
    }

    private static Map<Field, Set<Field>> determineFieldDependencies(List<Field> fields) {
        Map<Field, Set<String>> depsByField = new HashMap<>();
        Map<String, Field> names = new HashMap<>();

        for (Field f : fields) {
            String fName = getJarName(f);
            if (fName != null) {
                if (names.put(fName, f) != null) {
                    throw new IllegalArgumentException("Name '" + fName + "' declared on multiple @JarSources.");
                }
            }

            Dependencies[] deps = f.getAnnotationsByType(Dependencies.class);
            for (Dependencies da : deps) {
                if (da.resolver() != AnnotatedDependencyResolver.class) {
                    continue;
                }

                for (String d : da.value()) {
                    depsByField.computeIfAbsent(f, __ -> new HashSet<>()).add(d);
                }
            }
        }

        return depsByField.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().stream().map(names::get).collect(toSet())));
    }

    private static List<Field> findEligibleFields(Class<?> testClass) {
        return Stream.of(testClass.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()))
                .filter(CompiledJarExtension::hasCompatibleType).filter(CompiledJarExtension::hasJarSources)
                .collect(toList());
    }

    private static boolean hasCompatibleType(Field f) {
        return CompiledJar.Environment.class.isAssignableFrom(f.getType())
                || CompiledJar.class.isAssignableFrom(f.getType());
    }

    private static boolean hasJarSources(Field f) {
        return f.getAnnotation(AllJarSources.class) != null || f.getAnnotation(JarSources.class) != null;
    }
}
