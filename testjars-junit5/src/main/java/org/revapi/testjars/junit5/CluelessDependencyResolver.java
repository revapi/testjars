package org.revapi.testjars.junit5;

import org.revapi.testjars.DependencyResolver;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * A helper class representing unknown dependencies. It never resolves anything successfully.
 */
class CluelessDependencyResolver implements DependencyResolver {
    static final CluelessDependencyResolver INSTANCE = new CluelessDependencyResolver();

    @Override
    public Set<File> resolve(String identifier) {
        return Collections.emptySet();
    }
}
