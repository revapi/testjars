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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

/**
 * Takes care of compiling jar files. Keeps track of what was compiled and can delete the files afterwards using the
 * {@link #cleanUp()} method.
 */
public final class CompilerManager {
    private static final Logger LOG = LoggerFactory.getLogger(CompilerManager.class);

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    private Map<File, Semaphore> compiledStuff = new HashMap<>();

    private ExecutorService compileProcess = Executors.newCachedThreadPool();

    /**
     * Instantiates a builder using which the contents of a compiled jar file can be composed.
     *
     * @return a builder to gather sources and resources to compile and compose a jar file
     */
    public JarBuilder createJar() {
        return createJar(new NoopDependencyResolver());
    }

    public JarBuilder createJar(DependencyResolver dependencyResolver) {
        return new JarBuilder(dependencyResolver);
    }

    /**
     * If you already have a compiled jar file, you can start analyzing its contents using this method.
     * <p>
     * Note that this file is not automatically deleted after a test as would a jar file built using the
     * {@link #createJar()} method. If you want this file to also be cleaned up, use the {@link #manage(File)} method.
     *
     * @param jarFile the jar file to analyze
     * @return object using which the classes within the jar file can be inspected.
     */
    public CompiledJar jarFrom(File jarFile) {
        return jarFrom(jarFile, new File[0]);
    }


    /**
     * If you already have a compiled jar file, you can start analyzing its contents using this method.
     * <p>
     * Note that the files are not automatically deleted after a test as would a jar file built using the
     * {@link #createJar()} method. If you want this file to also be cleaned up, use the {@link #manage(File)} method.
     *
     * @param jarFile the jar file to analyze
     * @param dependencies the additional dependencies that need to be present on the classpath to be able to analyze
     *                     the jar file
     * @return object using which the classes within the jar file can be inspected.
     */
    public CompiledJar jarFrom(File jarFile, File... dependencies) {
        return new CompiledJar(jarFile, null, dependencies, this);
    }

    /**
     * Given file will be automatically cleaned up after the test.
     *
     * @param jarFile a file to delete once the test is finished.
     */
    public void manage(File jarFile) {
        compiledStuff.put(jarFile, null);
    }

    /**
     * If you're using the Jar instance as a JUnit rule, you don't have to call this method. Otherwise this can be used
     * to remove the compiled jar files from the filesystem.
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions"})
    public void cleanUp() {
        for (Map.Entry<File, Semaphore> e : compiledStuff.entrySet()) {
            if (e.getValue() != null) {
                e.getValue().release();
            }

            try {
                Files.walkFileTree(e.getKey().toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ex) {
                LOG.warn("Failed to clean up directory " + e.getKey().getAbsolutePath(), ex);
            }
        }
    }

    CompiledJar.Environment probe(CompiledJar compiledJar) {
        File dir = new File(compiledJar.jarFile().getParent(), "probe");
        if (!dir.mkdirs()) {
            throw new IllegalArgumentException("Failed to create directory " + dir.getAbsolutePath());
        }

        String classpathString = compiledJar.classpath().isEmpty()
                ? compiledJar.jarFile().getAbsolutePath()
                : Stream.concat(Stream.of(compiledJar.jarFile()), compiledJar.classpath().stream())
                .map(File::getAbsolutePath).collect(joining(File.pathSeparator));

        List<String> options = Arrays.asList("-cp", classpathString,
                "-d", dir.getAbsolutePath());

        List<JavaFileObject> sourceObjects = new ArrayList<>(2);
        sourceObjects.add(new MarkerAnnotationObject());
        sourceObjects.add(new ArchiveProbeObject());

        StandardJavaFileManager fileManager = compiler
                .getStandardFileManager(null, Locale.getDefault(), Charset.forName("UTF-8"));

        JavaCompiler.CompilationTask task = compiler
                .getTask(new PrintWriter(System.out), fileManager, null, options, singletonList(ArchiveProbeObject.CLASS_NAME), sourceObjects);


        final Semaphore cleanUpSemaphore = new Semaphore(0);
        final Semaphore initSemaphore = new Semaphore(0);

        final CompiledJar.Environment ret = new CompiledJar.Environment();

        task.setProcessors(singletonList(new AbstractProcessor() {
            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.latest();
            }

            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return new HashSet<>(singletonList(MarkerAnnotationObject.CLASS_NAME));
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (roundEnv.processingOver()) {
                    ret.elements = processingEnv.getElementUtils();
                    ret.types = processingEnv.getTypeUtils();
                    ret.processingEnvironment = processingEnv;

                    initSemaphore.release();

                    try {
                        cleanUpSemaphore.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    return true;
                }

                return false;
            }
        }));

        compileProcess.submit(task);

        try {
            initSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted");
        }

        compiledStuff.put(compiledJar.jarFile().getParentFile(), cleanUpSemaphore);

        return ret;
    }

    public final class JarBuilder {
        private final DependencyResolver dependencyResolver;
        private final List<File> dependencies = new ArrayList<>();
        private Map<URI, JavaFileObject> sources = new HashMap<>();
        private Map<URI, InputStream> resources = new HashMap<>();

        private JarBuilder(DependencyResolver dependencyResolver) {
            this.dependencyResolver = dependencyResolver;
        }

        /**
         * Finds given sources under given root in the classpath. The resulting jar file will contain the compiled
         * classes on the same relatives paths as the provided sources.
         *
         * @param root    the root path in the classloader to resolve the sources against
         * @param sources the list of relative paths on which the source files are located in the classloader
         * @return this instance
         */
        public JarBuilder classPathSources(String root, String... sources) {
            URI rootUri = toUri(root);

            for (String source : sources) {
                URI sourceUri = URI.create(source);
                URI location = rootUri.resolve(sourceUri);

                this.sources.put(sourceUri, new SourceInClassLoader(sourceUri, location));
            }

            return this;
        }

        /**
         * Adds given resources to the compiled jar file. The paths to the resources are resolved in the same way
         * as with sources.
         *
         * @param root      the root against which to resolve the resource paths in the classloader
         * @param resources the relative paths of the resources
         * @return this instance
         * @see #classPathSources(String, String...)
         */
        public JarBuilder classPathResources(String root, String... resources) {
            URI rootUri = toUri(root);

            for (String resource : resources) {
                URI resourceUri = URI.create(resource);
                URI location = rootUri.resolve(resourceUri);

                this.resources.put(resourceUri, getClass().getResourceAsStream(location.getPath()));
            }

            return this;
        }

        /**
         * Similar to {@link #classPathSources(String, String...)} but locates the sources to compile using actual
         * files.
         */
        public JarBuilder fileSources(File root, File... sources) {
            URI rootUri = root.toURI();

            for (File source : sources) {
                URI sourceUri = URI.create(source.getPath());
                URI location = rootUri.resolve(sourceUri);

                this.sources.put(sourceUri, new FileJavaFileObject(sourceUri, new File(location.getPath())));
            }

            return this;
        }

        /**
         * Similar to {@link #classPathResources(String, String...)} but locates the sources to compile using actual
         * files.
         */
        public JarBuilder fileResources(File root, File... resources) {
            URI rootUri = root.toURI();

            for (File resource : resources) {
                URI resourceUri = URI.create(resource.getPath());
                URI location = rootUri.resolve(resourceUri);

                try {
                    this.resources.put(resourceUri, new FileInputStream(location.getPath()));
                } catch (FileNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            return this;
        }

        public JarBuilder dependencies(String identifier, String... moreIdentifiers) {
            for (int i = -1; i < moreIdentifiers.length; ++i) {
                String id = i == -1 ? identifier : moreIdentifiers[i];
                dependencies.addAll(dependencyResolver.resolve(id));
            }
            return this;
        }

        /**
         * Unlike with {@link #dependencies(String, String...)} where the configured dependency resolver is responsible
         * to locate all the transitive dependencies, using this "manual" method, the caller needs to make sure that
         * the provided dependencies are complete, i.e. that all the transitive dependencies are also supplied.
         *
         * @param jarFile the jar file of the dependency
         * @param jarFiles other dependencies
         */
        public JarBuilder dependencies(File jarFile, File... jarFiles) {
            dependencies.add(jarFile);
            dependencies.addAll(Arrays.asList(jarFiles));
            return this;
        }

        /**
         * Compiles the sources and composes a jar file that comprises of the class files on the specified locations
         * (defined by {@link #classPathSources(String, String...)} et al.) along with some resources on the specified
         * locations (defined by {@link #classPathResources(String, String...)} et al.).
         *
         * @return an object to access the results of the compilation
         * @throws IOException on error
         */
        public CompiledJar build() throws IOException {
            File dir = Files.createTempDirectory("revapi-testjars").toFile();

            File compiledSourcesOutput = new File(dir, "classes");
            if (!compiledSourcesOutput.mkdirs()) {
                throw new IllegalStateException("Could not create output location for compiling test sources.");
            }

            List<JavaFileObject> sourceObjects = new ArrayList<>(sources.values());

            List<String> options = new ArrayList<>(4);
            options.add("-d");
            options.add(compiledSourcesOutput.getAbsolutePath());
            if (!dependencies.isEmpty()) {
                options.add("-cp");
                options.add(dependencies.stream()
                        .map(File::getAbsolutePath).collect(joining(File.pathSeparator)));
            }

            JavaCompiler.CompilationTask firstCompilation = compiler.getTask(null, null, null, options, null, sourceObjects);
            if (!firstCompilation.call()) {
                throw new IllegalStateException("Failed to compile the sources");
            }

            for (Map.Entry<URI, InputStream> e : resources.entrySet()) {
                File target = new File(compiledSourcesOutput, e.getKey().getPath());
                File parent = target.getParentFile();
                if (!parent.exists()) {
                    if (!parent.mkdirs()) {
                        throw new IllegalStateException("Failed to create directory " + target.getParentFile().getAbsolutePath());
                    }
                }
                Files.copy(e.getValue(), target.toPath());
            }

            File compiledJar = new File(dir, "compiled.jar");
            try (JarOutputStream out = new JarOutputStream(new FileOutputStream(compiledJar))) {
                Path root = compiledSourcesOutput.toPath();
                HashSet<String> added = new HashSet<>();

                // The JAR file spec assumes that the MANIFEST.MF is the first or the second entry in the jar file.
                // Because we don't know what we're putting in, we have to manually scan what we were instructed to
                // put in the jar file and try to find the MANIFEST.MF.
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if ("MANIFEST.MF".equals(file.getFileName().toString())
                                && "META-INF".equals(file.getParent().getFileName().toString())) {

                            ZipEntry entry = new ZipEntry("META-INF/");
                            out.putNextEntry(entry);
                            out.closeEntry();

                            entry = new ZipEntry("META-INF/MANIFEST.MF");
                            out.putNextEntry(entry);
                            Files.copy(file, out);
                            out.closeEntry();

                            added.add("META-INF/");
                            added.add("META-INF/MANIFEST.MF");
                        }
                        return super.visitFile(file, attrs);
                    }
                });

                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        StringBuilder path = new StringBuilder();
                        Iterator<Path> it = root.relativize(file).iterator();
                        while (it.hasNext()) {
                            Path p = it.next();
                            boolean isDir = it.hasNext();

                            path.append(p.toString());
                            if (isDir) {
                                path.append("/");
                            }

                            String currentPath = path.toString();

                            if (added.contains(currentPath)) {
                                continue;
                            }

                            ZipEntry entry = new ZipEntry(currentPath);
                            out.putNextEntry(entry);

                            if (!isDir) {
                                Files.copy(file, out);
                            }

                            out.closeEntry();

                            added.add(currentPath);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            compiledStuff.put(dir, null);

            return new CompiledJar(compiledJar, compiledSourcesOutput, dependencies.toArray(new File[0]), CompilerManager.this);
        }

        private URI toUri(String path) {
            if (path == null || path.isEmpty()) {
                return URI.create("/");
            } else {
                return URI.create(path);
            }
        }
    }

    private static final class NoopDependencyResolver implements DependencyResolver {

        @Override
        public Set<File> resolve(String identifier) {
            return emptySet();
        }
    }
}
