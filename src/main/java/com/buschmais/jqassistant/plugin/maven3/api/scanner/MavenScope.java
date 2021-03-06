package com.buschmais.jqassistant.plugin.maven3.api.scanner;

import com.buschmais.jqassistant.core.scanner.api.Scope;

/**
 * Defines the scopes for maven.
 */
public enum MavenScope implements Scope {

    PROJECT, REPOSITORY;

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getPrefix() {
        return "maven";
    }
}
