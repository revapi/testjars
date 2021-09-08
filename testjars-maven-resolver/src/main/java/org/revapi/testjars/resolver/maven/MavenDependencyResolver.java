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
package org.revapi.testjars.resolver.maven;

import static java.util.Collections.singletonList;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.revapi.testjars.DependencyResolver;

public class MavenDependencyResolver implements DependencyResolver {
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remoteRepositories;

    public MavenDependencyResolver() {
        this.repositorySystem = MavenBootstrap.newRepositorySystem();
        this.session = MavenBootstrap.newRepositorySystemSession(repositorySystem, MavenBootstrap.homeM2Repository());
        this.remoteRepositories = singletonList(MavenBootstrap.mavenCentral());
    }

    public MavenDependencyResolver(RepositorySystem repositorySystem, RepositorySystemSession session,
            List<RemoteRepository> remoteRepositories) {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.remoteRepositories = remoteRepositories;
    }

    @Override
    public Set<File> resolve(String identifier) {
        return resolveDependencies(new DefaultArtifact(identifier)).map(Artifact::getFile).collect(Collectors.toSet());
    }

    private Stream<Artifact> resolveDependencies(Artifact artifact) {
        CollectRequest collectRequest = new CollectRequest(new Dependency(artifact, null), remoteRepositories);

        DependencyRequest request = new DependencyRequest(collectRequest, null);

        DependencyResult result;

        try {
            result = repositorySystem.resolveDependencies(session, request);
        } catch (DependencyResolutionException dre) {
            result = dre.getResult();
        }

        if (result == null) {
            return Stream.empty();
        }

        return result.getArtifactResults().stream().map(ArtifactResult::getArtifact);
    }
}
