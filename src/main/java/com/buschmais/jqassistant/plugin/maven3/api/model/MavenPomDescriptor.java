package com.buschmais.jqassistant.plugin.maven3.api.model;

import java.util.List;
import java.util.Set;

import com.buschmais.jqassistant.plugin.common.api.model.ArtifactDescriptor;
import com.buschmais.jqassistant.plugin.common.api.model.NamedDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Property;
import com.buschmais.xo.neo4j.api.annotation.Relation;

import org.apache.maven.model.Model;

/**
 * Descriptor for a POM.
 *
 * @see Model
 * @author ronald.kunzmann@buschmais.com
 */
@Label(value = "Pom")
public interface MavenPomDescriptor extends MavenDescriptor, BaseProfileDescriptor, MavenCoordinatesDescriptor, MavenDependentDescriptor, NamedDescriptor {

    /**
     * Get the artifacts which are described by this POM.
     *
     * @return The described artifacts.
     */
    @Relation("DESCRIBES")
    Set<ArtifactDescriptor> getDescribes();

    /**
     * Get the parent POM.
     *
     * @return The parent POM.
     */
    @Relation("HAS_PARENT")
    ArtifactDescriptor getParent();

    /**
     * Set the parent POM.
     *
     * @param parent
     *            The parent POM.
     */
    void setParent(ArtifactDescriptor parent);

    /**
     * Get referenced licenses.
     *
     * @return The licenses.
     */
    @Relation("USES_LICENSE")
    List<MavenLicenseDescriptor> getLicenses();

    /**
     * Get referenced developers.
     *
     * @return The developers.
     */
    @Relation("HAS_DEVELOPER")
    List<MavenDeveloperDescriptor> getDevelopers();

    /**
     * Returns all mentioned contributors.
     *
     * @return A list of all mentioned contributors.
     */
    @Relation("HAS_CONTRIBUTOR")
    List<MavenContributorDescriptor> getContributors();

    /**
     * Returns all declared repositories for this POM.
     *
     * @return A list of all declared repositories
     */
    @Relation("HAS_REPOSITORY")
    List<MavenRepositoryDescriptor> getRepositories();

    /**
     * Get profile information.
     *
     * @return The profiles.
     */
    @Relation("HAS_PROFILE")
    List<MavenProfileDescriptor> getProfiles();

    /**
     * Gets the organization behind the project.
     *
     * @return The organization behind the project.
     */
    @Relation("HAS_ORGANIZATION")
    MavenOrganizationDescriptor getOrganization();

    /**
     * Sets the organization behind the project.
     *
     * @param organization
     *            The organisation behind the project.
     */
    void setOrganization(MavenOrganizationDescriptor organization);

    @Relation("HAS_SCM")
    MavenScmDescriptor getScm();

    /**
     *
     * @param scmDescriptor
     */
    void setScm(MavenScmDescriptor scmDescriptor);

    /**
     * Returns the URL of the project home.
     *
     * @return the URL of the project home or `null` if this information is present.
     */
    @Property("url")
    String getUrl();

    /**
     * Sets the URL of the project home.
     *
     * @param url
     *            the URL of the project home.
     */
    void setUrl(String url);

    /**
     * Returns the description of the POM.
     *
     * @return the URL of the project home or `null` if this information is present.
     */
    @Property("description")
    String getDescription();

    /**
     * Sets the description of the POM.
     *
     * @param description
     *            the description of the POM.
     */
    void setDescription(String description);
}
