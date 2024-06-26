package com.buschmais.jqassistant.plugin.maven3.test.scanner;

import java.io.File;
import java.util.*;

import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.plugin.common.api.model.ArtifactDescriptor;
import com.buschmais.jqassistant.plugin.common.api.model.PropertyDescriptor;
import com.buschmais.jqassistant.plugin.common.api.model.ValueDescriptor;
import com.buschmais.jqassistant.plugin.java.api.scanner.JavaScope;
import com.buschmais.jqassistant.plugin.java.test.AbstractJavaPluginIT;
import com.buschmais.jqassistant.plugin.maven3.api.artifact.ArtifactResolver;
import com.buschmais.jqassistant.plugin.maven3.api.artifact.Coordinates;
import com.buschmais.jqassistant.plugin.maven3.api.model.*;
import com.buschmais.jqassistant.plugin.maven3.impl.scanner.artifact.MavenArtifactResolver;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

class MavenPomXmlFileScannerIT extends AbstractJavaPluginIT {

    /**
     * Scans and tests pom.xml files.
     *
     */
    @Test
    void artifactResolver() {
        final File directory = getClassesDirectory(MavenPomXmlFileScannerIT.class);
        final ArtifactResolver artifactResolverSpy = Mockito.spy(new MavenArtifactResolver());
        execute(ARTIFACT_ID, (artifact, scanner) -> {
            ScannerContext context = scanner.getContext();
            context.push(ArtifactResolver.class, artifactResolverSpy);
            scanner.scan(directory, directory.getAbsolutePath(), JavaScope.CLASSPATH);
            context.pop(ArtifactResolver.class);
            return Collections.emptyList();
        });
        verify(artifactResolverSpy, atLeastOnce()).resolve(Mockito.any(Coordinates.class), Mockito.any(ScannerContext.class));
        store.beginTransaction();
        validateParentPom();
        validateChildPom();
        store.commitTransaction();
    }

    /**
     * Scans and tests pom.xml files.
     *
     */
    @Test
    void pomModel() {
        scanClassPathDirectory(getClassesDirectory(MavenPomXmlFileScannerIT.class));
        store.beginTransaction();
        validateParentPom();
        validateChildPom();
        store.commitTransaction();
    }

    @Test
    void urlOfTheProjectHomeIsAvailableAsProperty() {
        scanClassPathResource(DefaultScope.NONE, "/pom.xml");
        store.beginTransaction();
        List<MavenPomDescriptor> pomDescriptors = query("MATCH (p:Maven:Pom) RETURN p").getColumn("p");
        assertThat(pomDescriptors).hasSize(1);
        MavenPomDescriptor pomDescriptor = pomDescriptors.get(0);
        assertThat(pomDescriptor.getUrl()).isEqualTo("https://github.com/buschmais/jqassistant");
        store.commitTransaction();
    }

    /**
     * Verifies that a dependency declared by a pom is resolved to a
     * {@link MavenDependencyDescriptor} providing {@link MavenExcludesDescriptor}.
     */
    @Test
    void pomDependenciesWithExclusion() {
        scanClassPathResource(DefaultScope.NONE, "/dependency/2/pom.xml");
        scanClassPathResource(DefaultScope.NONE, "/dependency/1/pom.xml");
        store.beginTransaction();
        MavenDependencyDescriptor dependencyDescriptor = store
            .executeQuery("MATCH (:Maven:Pom:Xml:File{artifactId:'test2'})-[:DECLARES_DEPENDENCY]->(d:Maven:Dependency) RETURN d")
            .getSingleResult().get("d", MavenDependencyDescriptor.class);
        assertThat(dependencyDescriptor).isNotNull();
        MavenArtifactDescriptor toArtifact = dependencyDescriptor.getToArtifact();
        assertThat(toArtifact.getFullQualifiedName()).isEqualTo("com.buschmais.jqassistant:test1:jar:1.0.0-SNAPSHOT");
        assertThat(dependencyDescriptor.isOptional()).isEqualTo(true);
        assertThat(dependencyDescriptor.getScope()).isEqualTo("runtime");
        List<MavenExcludesDescriptor> exclusions = dependencyDescriptor.getExclusions();
        assertThat(exclusions.size()).isEqualTo(1);
        MavenExcludesDescriptor excludesDescriptor = exclusions.get(0);
        assertThat(excludesDescriptor.getGroupId()).isEqualTo("org.apache.commons");
        assertThat(excludesDescriptor.getArtifactId()).isEqualTo("commons-lang3");
        store.commitTransaction();
    }

    @Test
    void pluginCanFindMavenPOMInXMLDocumentWithNonStandardName() {
        scanClassPathDirectory(getClassesDirectory(MavenPomXmlFileScannerIT.class));

        store.beginTransaction();

        List<MavenPomXmlDescriptor> mavenPomDescriptors = query("MATCH (n:File:Maven:Xml:Pom) WHERE n.fileName=~ \".*/dependency-reduced-pom-file.xml\" RETURN n").getColumn("n");
        assertThat(mavenPomDescriptors).hasSize(1);

        store.commitTransaction();
    }

    /**
     * Scans an invalid pom.xml file.
     *
     */
    @Test
    void invalidPomFile() {
        scanClassPathResource(JavaScope.CLASSPATH,"/invalid/pom.xml");
        store.beginTransaction();
        List<MavenPomXmlDescriptor> mavenPomDescriptors = query("MATCH (n:File:Maven:Xml:Pom) WHERE n.xmlWellFormed=false RETURN n").getColumn("n");
        assertThat(mavenPomDescriptors).hasSize(1);

        store.commitTransaction();
    }

    @Test
    void organizationInPomFile() {
        scanClassPathResource(JavaScope.CLASSPATH,"/with-organization/pom.xml");
        store.beginTransaction();
        List<MavenOrganizationDescriptor> organisationDescriptors = query("MATCH (o:Organization:Maven) RETURN o").getColumn("o");
        assertThat(organisationDescriptors).isNotNull();
        assertThat(organisationDescriptors).hasSize(1);

        MavenOrganizationDescriptor organisationDescriptor = organisationDescriptors.get(0);

        assertThat(organisationDescriptor.getName()).isEqualTo("The Quality Analyzer");
        assertThat(organisationDescriptor.getUrl()).isEqualTo("http://jqassistant.org");

        store.commitTransaction();
    }

    @Test
    void relationBetweenPOMAndOrganisationWorks() {
        scanClassPathResource(JavaScope.CLASSPATH, "/with-organization/pom.xml");
        store.beginTransaction();
        List<MavenOrganizationDescriptor> organisationDescriptors =
                query("MATCH (p:Maven:Pom)-[:HAS_ORGANIZATION]->(o:Organization:Maven) RETURN o").getColumn("o");
        assertThat(organisationDescriptors).isNotNull();
        assertThat(organisationDescriptors).hasSize(1);

        MavenOrganizationDescriptor organisationDescriptor = organisationDescriptors.get(0);

        assertThat(organisationDescriptor.getName()).isEqualTo("The Quality Analyzer");
        assertThat(organisationDescriptor.getUrl()).isEqualTo("http://jqassistant.org");

        store.commitTransaction();
    }

    @Test
    void minimalRepositoryInPOM() {
        scanClassPathResource(JavaScope.CLASSPATH, "/repository/1/pom.xml");

        store.beginTransaction();

        List<MavenRepositoryDescriptor> repoDescriptors =
                query("MATCH (f:File:Maven:Xml:Pom) -[:HAS_REPOSITORY]->(r:Maven:Repository) " +
                        "WHERE f.fileName='/repository/1/pom.xml' " +
                        "RETURN r").getColumn("r");

        assertThat(repoDescriptors).hasSize(1);

        MavenRepositoryDescriptor repoDescriptor = repoDescriptors.get(0);

        assertThat(repoDescriptor.getName()).isEqualTo("Repo");
        assertThat(repoDescriptor.getId()).isEqualTo("x1");
        assertThat(repoDescriptor.getUrl()).isEqualTo("https://repo.org");
        assertThat(repoDescriptor.getLayout()).isEqualTo("default");

        assertThat(repoDescriptor.getReleasesChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getReleasesEnabled()).isEqualTo(true);
        assertThat(repoDescriptor.getReleasesUpdatePolicy()).isEqualTo("daily");

        assertThat(repoDescriptor.getSnapshotsChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getSnapshotsEnabled()).isEqualTo(true);
        assertThat(repoDescriptor.getSnapshotsUpdatePolicy()).isEqualTo("daily");
        store.commitTransaction();
    }

    @Test
    void disabledReleasesForRepositoryInPOM() {
        scanClassPathResource(JavaScope.CLASSPATH, "/repository/2/pom.xml");

        store.beginTransaction();

        List<MavenRepositoryDescriptor> repoDescriptors =
                query("MATCH (f:File:Maven:Xml:Pom) -[:HAS_REPOSITORY]->(r:Maven:Repository) " +
                        "WHERE f.fileName='/repository/2/pom.xml' " +
                        "RETURN r").getColumn("r");

        assertThat(repoDescriptors).hasSize(1);

        MavenRepositoryDescriptor repoDescriptor = repoDescriptors.get(0);

        assertThat(repoDescriptor.getName()).isEqualTo("Repo");
        assertThat(repoDescriptor.getId()).isEqualTo("x1");
        assertThat(repoDescriptor.getUrl()).isEqualTo("https://repo.org");
        assertThat(repoDescriptor.getLayout()).isEqualTo("default");

        assertThat(repoDescriptor.getReleasesChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getReleasesEnabled()).isEqualTo(false);
        assertThat(repoDescriptor.getReleasesUpdatePolicy()).isEqualTo("daily");

        assertThat(repoDescriptor.getSnapshotsChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getSnapshotsEnabled()).isEqualTo(true);
        assertThat(repoDescriptor.getSnapshotsUpdatePolicy()).isEqualTo("daily");
        store.commitTransaction();
    }

    @Test
    void disabledSnapshotsForRepositoryInPOM() {
        scanClassPathResource(JavaScope.CLASSPATH, "/repository/3/pom.xml");

        store.beginTransaction();

        List<MavenRepositoryDescriptor> repoDescriptors =
                query("MATCH (f:File:Maven:Xml:Pom) -[:HAS_REPOSITORY]->(r:Maven:Repository) " +
                        "WHERE f.fileName='/repository/3/pom.xml' " +
                        "RETURN r").getColumn("r");

        assertThat(repoDescriptors).hasSize(1);

        MavenRepositoryDescriptor repoDescriptor = repoDescriptors.get(0);

        assertThat(repoDescriptor.getName()).isEqualTo("Repo");
        assertThat(repoDescriptor.getId()).isEqualTo("x1");
        assertThat(repoDescriptor.getUrl()).isEqualTo("https://repo.org");
        assertThat(repoDescriptor.getLayout()).isEqualTo("default");

        assertThat(repoDescriptor.getReleasesChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getReleasesEnabled()).isEqualTo(true);
        assertThat(repoDescriptor.getReleasesUpdatePolicy()).isEqualTo("daily");

        assertThat(repoDescriptor.getSnapshotsChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getSnapshotsEnabled()).isEqualTo(false);
        assertThat(repoDescriptor.getSnapshotsUpdatePolicy()).isEqualTo("daily");
        store.commitTransaction();
    }

    @Test
    void minimalRepositoryInProfile() {
        scanClassPathResource(JavaScope.CLASSPATH, "/repository/4/pom.xml");

        store.beginTransaction();

        List<MavenRepositoryDescriptor> repoDescriptors =
                query("MATCH (f:File:Maven:Xml:Pom) " +
                        "-[:HAS_PROFILE]->(:Maven:Profile) " +
                        "-[:HAS_REPOSITORY]->(r:Maven:Repository) " +
                        "WHERE f.fileName='/repository/4/pom.xml' " +
                        "RETURN r").getColumn("r");

        assertThat(repoDescriptors).hasSize(1);

        MavenRepositoryDescriptor repoDescriptor = repoDescriptors.get(0);

        assertThat(repoDescriptor.getName()).isEqualTo("Repo");
        assertThat(repoDescriptor.getId()).isEqualTo("x1");
        assertThat(repoDescriptor.getUrl()).isEqualTo("https://repo.org");
        assertThat(repoDescriptor.getLayout()).isEqualTo("default");

        assertThat(repoDescriptor.getReleasesChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getReleasesEnabled()).isEqualTo(true);
        assertThat(repoDescriptor.getReleasesUpdatePolicy()).isEqualTo("daily");

        assertThat(repoDescriptor.getSnapshotsChecksumPolicy()).isEqualTo("warn");
        assertThat(repoDescriptor.getSnapshotsEnabled()).isEqualTo(true);
        assertThat(repoDescriptor.getSnapshotsUpdatePolicy()).isEqualTo("daily");
        store.commitTransaction();
    }

    /**
     * Checks if all developers in a given pom.xml will be found and
     * added to the model.
     */
    @Test
    void allDevelopersAreFound() {
        scanClassPathResource(JavaScope.CLASSPATH, "/with-developers/pom.xml");

        store.beginTransaction();

        List<MavenPomXmlDescriptor> pomDescriptors = query("MATCH (n:File:Maven:Xml:Pom) " +
                                                           "WHERE n.fileName='/with-developers/pom.xml' " +
                                                           "RETURN n").getColumn("n");

        assertThat(pomDescriptors).hasSize(1);

        MavenPomDescriptor descriptor = pomDescriptors.get(0);

        assertThat(descriptor.getDevelopers()).hasSize(1);

        MavenDeveloperDescriptor developer = descriptor.getDevelopers().stream().findFirst().get();

        assertThat(developer.getId()).isEqualTo("he");
        assertThat(developer.getName()).isEqualTo("Alexej Alexandrowitsch Karenin");
        assertThat(developer.getOrganization()).isEqualTo("Tolstoi's World");
        assertThat(developer.getOrganizationUrl()).isEqualTo("http://www.tolstoi.org");
        assertThat(developer.getEmail()).isEqualTo("aak@tolstoi.org");
        assertThat(developer.getTimezone()).isEqualTo("+2");
        assertThat(developer.getUrl()).isEqualTo("http://www.tolstoi.org/~aak/");

        assertThat(developer.getRoles()).hasSize(3);

        List<MavenParticipantRoleDescriptor> roles = developer.getRoles();

        assertThat(roles.stream().map(role -> role.getName()).collect(toList())).containsExactlyInAnyOrder("husband", "public officer", "father");

        List<MavenDeveloperDescriptor> developers = query("MATCH (d:Maven:Developer:Participant) " +
                                                          "WHERE not(d:Contributor) RETURN d")
                                                        .getColumn("d");

        assertThat(developers).hasSize(1);
        assertThat(developers.get(0).getId()).isEqualTo("he");
        store.commitTransaction();
    }

    /**
     * Checks if all contributors in a given pom.xml will be found and
     * added to the model.
     */
    @Test
    void allContributorsAreFound() {
        scanClassPathResource(JavaScope.CLASSPATH, "/with-developers/pom.xml");

        store.beginTransaction();

        List<MavenPomXmlDescriptor> pomDescriptors = query("MATCH (n:File:Maven:Xml:Pom) " +
                                                           "WHERE n.fileName='/with-developers/pom.xml' " +
                                                           "RETURN n").getColumn("n");

        assertThat(pomDescriptors).hasSize(1);

        MavenPomDescriptor descriptor = pomDescriptors.get(0);

        assertThat(descriptor.getContributors()).hasSize(1);

        MavenContributorDescriptor contributor = descriptor.getContributors().stream().findFirst().get();

        assertThat(contributor.getName()).isEqualTo("Till Eulenspiegel");
        assertThat(contributor.getOrganization()).isEqualTo("Familie Eulenspiegel");
        assertThat(contributor.getOrganizationUrl()).isEqualTo("http://www.eulenspiegel.org");
        assertThat(contributor.getEmail()).isEqualTo("till@eulenspiegel.org");
        assertThat(contributor.getTimezone()).isEqualTo("+1");
        assertThat(contributor.getUrl()).isEqualTo("http://www.eulenspiegel.org/~till/");

        assertThat(contributor.getRoles()).hasSize(1);

        List<MavenParticipantRoleDescriptor> roles = contributor.getRoles();

        assertThat(roles.stream().map(role -> role.getName()).collect(toList())).containsExactlyInAnyOrder("Narr");

        List<MavenContributorDescriptor> developers = query("MATCH (c:Maven:Contributor:Participant) " +
                                                            "WHERE not(c:Developer) RETURN c")
            .getColumn("c");

        assertThat(developers).hasSize(1);
        assertThat(developers.get(0).getEmail()).isEqualTo("till@eulenspiegel.org");
        store.commitTransaction();
    }

    /**
     * Validates child pom.
     */
    private void validateChildPom() {
        List<MavenPomXmlDescriptor> pomDescriptors = query("MATCH (n:File:Maven:Xml:Pom) WHERE n.fileName='/child/pom.xml' RETURN n").getColumn("n");
        assertThat(pomDescriptors).hasSize(1);

        MavenPomXmlDescriptor pomDescriptor = pomDescriptors.get(0);
        Assert.assertNull(pomDescriptor.getGroupId());
        assertThat(pomDescriptor.getArtifactId()).isEqualTo("jqassistant.child");
        assertThat(pomDescriptor.getVersion()).isNull();
        assertThat(pomDescriptor.getDescription()).isNull();

        ArtifactDescriptor parentDescriptor = pomDescriptor.getParent();
        assertThat(parentDescriptor.getGroup()).isEqualTo("com.buschmais.jqassistant");
        assertThat(parentDescriptor.getName()).isEqualTo("jqassistant.parent");
        assertThat(parentDescriptor.getVersion()).isEqualTo("1.0.0-RC-SNAPSHOT");

        // validate dependencies
        Map<String, Object> params = new HashMap<>();
        params.put("pom", pomDescriptor);
        List<MavenDependencyDescriptor> dependencyDescriptors = query(
                "MATCH (pom:Pom)-[:DECLARES_DEPENDENCY]->(d:Maven:Dependency) WHERE id(pom)=$pom RETURN d", params).getColumn("d");

        assertThat(dependencyDescriptors).hasSize(4);

        List<Dependency> dependencyList = createChildDependencies();
        for (Dependency dependency : dependencyList) {
            verifyDependency(dependencyDescriptors, dependency);
        }

        assertThat(pomDescriptor.getProperties()).isEmpty();
        assertThat(pomDescriptor.getManagesDependencies()).isEmpty();
        assertThat(pomDescriptor.getManagedPlugins()).isEmpty();
        assertThat(pomDescriptor.getPlugins()).isEmpty();
        assertThat(pomDescriptor.getModules()).isEmpty();
    }

    /**
     * Validates dependency existence. Fails if no dependency containing all
     * given fields exists.
     *
     * @param dependencyDescriptors
     *            Descriptors containing all dependencies.
     * @param dependency
     *            expected dependency informations.
     */
    private void verifyDependency(List<MavenDependencyDescriptor> dependencyDescriptors, Dependency dependency) {
        for (MavenDependencyDescriptor mavenDependencyDescriptor : dependencyDescriptors) {
            MavenArtifactDescriptor dependencyDescriptor = mavenDependencyDescriptor.getToArtifact();
            if (Objects.equals(dependencyDescriptor.getGroup(), dependency.group) && //
                    Objects.equals(dependencyDescriptor.getName(), dependency.name) && //
                    Objects.equals(dependencyDescriptor.getClassifier(), dependency.classifier) && //
                    Objects.equals(dependencyDescriptor.getType(), dependency.type) && //
                    Objects.equals(dependencyDescriptor.getVersion(), dependency.version) && //
                    Objects.equals(mavenDependencyDescriptor.getScope(), dependency.scope)) {
                return;
            }
        }
        Assert.fail("Dependency not found: " + dependency.toString());
    }

    /**
     * Validates parent pom.
     */
    private void validateParentPom() {
        List<MavenPomXmlDescriptor> mavenPomDescriptors = query("MATCH (n:File:Maven:Xml:Pom) WHERE n.fileName='/pom.xml' RETURN n").getColumn("n");
        assertThat(mavenPomDescriptors).hasSize(1);

        MavenPomXmlDescriptor pomDescriptor = mavenPomDescriptors.iterator().next();
        assertThat(pomDescriptor.getGroupId()).isEqualTo("com.buschmais.jqassistant");
        assertThat(pomDescriptor.getArtifactId()).isEqualTo("jqassistant.parent");
        assertThat(pomDescriptor.getVersion()).isEqualTo("1.0.0-RC-SNAPSHOT");
        assertThat(pomDescriptor.getDescription()).isEqualTo("Framework for structural analysis of Java applications.");

        ArtifactDescriptor parentDescriptor = pomDescriptor.getParent();
        assertThat(parentDescriptor).isNull();

        List<MavenLicenseDescriptor> licenseDescriptors = pomDescriptor.getLicenses();
        assertThat(mavenPomDescriptors).hasSize(1);
        MavenLicenseDescriptor licenseDescriptor = licenseDescriptors.iterator().next();
        assertThat(licenseDescriptor.getName()).isEqualTo("GNU General Public License, v3");
        assertThat(licenseDescriptor.getUrl()).isEqualTo("http://www.gnu.org/licenses/gpl-3.0.html");

        // dependency management
        Map<String,Object> params = new HashMap<>();
        params.put("pom", pomDescriptor);
        List<MavenDependencyDescriptor> managedDependencyDescriptors = query(
                "MATCH (pom)-[:MANAGES_DEPENDENCY]->(d:Maven:Dependency) WHERE id(pom)=$pom RETURN d", params).getColumn("d");
        List<Dependency> managedDependencies = getExpectedManagedParentDependencies();
        assertThat(managedDependencyDescriptors).hasSize(managedDependencies.size());

        for (Dependency dependency : managedDependencies) {
            verifyDependency(managedDependencyDescriptors, dependency);
        }

        assertThat(pomDescriptor.getDeclaresDependencies()).isEmpty();

        // properties
        List<PropertyDescriptor> propertyDescriptors = pomDescriptor.getProperties();
        Properties properties = new Properties();
        properties.put("project.build.sourceEncoding", "UTF-8");
        properties.put("org.slf4j_version", "1.7.5");

        validateProperties(propertyDescriptors, properties);

        // modules
        List<MavenModuleDescriptor> modules = pomDescriptor.getModules();
        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).getName()).isEqualTo("child");

        // plugins
        List<MavenPluginDescriptor> pluginDescriptors = pomDescriptor.getPlugins();
        List<Plugin> plugins = createParentPlugins();
        assertThat(plugins).hasSize(pluginDescriptors.size());

        for (Plugin plugin : plugins) {
            checkPlugin(pluginDescriptors, plugin);
        }

        // managed plugins
        List<MavenPluginDescriptor> managedPluginDescriptors = pomDescriptor.getManagedPlugins();
        List<Plugin> managedPlugins = createManagedParentPlugins();
        assertThat(managedPlugins).hasSize(managedPluginDescriptors.size());

        for (Plugin plugin : managedPlugins) {
            checkPlugin(managedPluginDescriptors, plugin);
        }

        // profiles
        List<MavenProfileDescriptor> profileDescriptors = pomDescriptor.getProfiles();
        List<Profile> parentProfiles = createParentProfiles();
        assertThat(profileDescriptors).hasSize(2);

        for (Profile profile : parentProfiles) {
            checkProfile(profileDescriptors, profile);
        }

    }

    private void validateProperties(List<PropertyDescriptor> propertyDescriptors, Properties properties) {
        assertThat(propertyDescriptors).hasSize(properties.size());

        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
            String value = properties.getProperty(propertyDescriptor.getName());
            assertThat(value).isNotNull();
            assertThat(propertyDescriptor.getValue()).isEqualTo(value);
        }
    }

    private void checkProfile(List<MavenProfileDescriptor> profileDescriptors, Profile profile) {
        for (MavenProfileDescriptor mavenProfileDescriptor : profileDescriptors) {
            if (mavenProfileDescriptor.getId().equals(profile.id)) {
                // dependencies
                List<Dependency> dependencies = profile.dependencies;
                Map<String, Object> params = new HashMap<>();
                params.put("profile", mavenProfileDescriptor);
                List<MavenDependencyDescriptor> profileDeps = query(
                        "MATCH (p:Maven:Profile)-[:DECLARES_DEPENDENCY]->(d:Maven:Dependency) WHERE id(p)=$profile RETURN d", params).getColumn("d");
                for (Dependency dependency : dependencies) {
                    verifyDependency(profileDeps, dependency);
                }

                // modules
                List<MavenModuleDescriptor> modules = mavenProfileDescriptor.getModules();
                assertThat(profile.modules).hasSize(modules.size());

                for (MavenModuleDescriptor mavenModuleDescriptor : modules) {
                    assertThat(profile.modules).contains(mavenModuleDescriptor.getName());
                }

                // properties
                validateProperties(mavenProfileDescriptor.getProperties(), profile.properties);

                // plugins
                List<Plugin> plugins = profile.plugins;
                for (Plugin plugin : plugins) {
                    checkPlugin(mavenProfileDescriptor.getPlugins(), plugin);
                }

                // managed plugins
                List<MavenPluginDescriptor> managedPlugins = mavenProfileDescriptor.getManagedPlugins();
                assertThat(profile.managedPlugins).hasSize(managedPlugins.size());

                for (Plugin plugin : profile.managedPlugins) {
                    checkPlugin(managedPlugins, plugin);
                }

                checkActivation(mavenProfileDescriptor.getActivation(), profile.activation);
            }
        }

    }

    private void checkActivation(MavenProfileActivationDescriptor activationDescriptor, ProfileActivation activation) {
        if (null != activation) {
            Assert.assertNotNull(activationDescriptor);
            assertThat(activation.jdk).isEqualTo(activationDescriptor.getJdk());
            assertThat(activation.activeByDefault).isEqualTo(activationDescriptor.isActiveByDefault());

            if (null != activation.fileExists || null != activation.fileMissing) {
                MavenActivationFileDescriptor activationFileDescriptor = activationDescriptor.getActivationFile();
                Assert.assertNotNull(activationFileDescriptor);
                assertThat(activationFileDescriptor).isNotNull();
                assertThat(activation.fileExists).isEqualTo(activationFileDescriptor.getExists());
                assertThat(activation.fileMissing).isEqualTo(activationFileDescriptor.getMissing());
            }

            if (null != activation.propertyName || null != activation.propertyValue) {
                PropertyDescriptor propertyDescriptor = activationDescriptor.getProperty();
                assertThat(propertyDescriptor).isNotNull();
                assertThat(activation.propertyName).isEqualTo(propertyDescriptor.getName());
                assertThat(activation.propertyValue).isEqualTo(propertyDescriptor.getValue());
            }

            if (null != activation.osArch || null != activation.osFamily || null != activation.osName || null != activation.osVersion) {
                MavenActivationOSDescriptor activationOSDescriptor = activationDescriptor.getActivationOS();
                assertThat(activationOSDescriptor).isNotNull();
                assertThat(activation.osArch).isEqualTo(activationOSDescriptor.getArch());
                assertThat(activation.osFamily).isEqualTo(activationOSDescriptor.getFamily());
                assertThat(activation.osName).isEqualTo(activationOSDescriptor.getName());
                assertThat(activation.osVersion).isEqualTo(activationOSDescriptor.getVersion());
            }
        }
    }

    private void checkPlugin(List<MavenPluginDescriptor> managedPluginDescriptors, Plugin plugin) {
        MavenPluginDescriptor mavenPluginDescriptor = validatePlugin(managedPluginDescriptors, plugin);
        checkConfiguration(mavenPluginDescriptor.getConfiguration(), plugin.configuration);
        List<MavenPluginExecutionDescriptor> executionDescriptors = mavenPluginDescriptor.getExecutions();
        assertThat(plugin.executions).hasSize(executionDescriptors.size());

        for (Execution execution : plugin.executions) {
            MavenPluginExecutionDescriptor pluginExecutionDescriptor = validatePluginExecution(executionDescriptors, execution);
            List<MavenExecutionGoalDescriptor> goalDescriptors = pluginExecutionDescriptor.getGoals();
            assertThat(execution.goals).hasSize(goalDescriptors.size());

            for (MavenExecutionGoalDescriptor goalDescriptor : goalDescriptors) {
                assertThat(execution.goals).as("Unexpected goal: " + goalDescriptor.getName()).contains(goalDescriptor.getName());
            }
            checkConfiguration(pluginExecutionDescriptor.getConfiguration(), execution.configuration);
        }
    }

    private void checkConfiguration(MavenConfigurationDescriptor configurationDescriptor, Configuration configuration) {
        if (null != configuration) {
            Assert.assertNotNull(configurationDescriptor);
            for (ConfigEntry entry : configuration.entries) {
                validateConfigurationEntry(configurationDescriptor.getValues(), entry);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateConfigurationEntry(List<ValueDescriptor<?>> descriptors, ConfigEntry entry) {
        for (ValueDescriptor<?> valueDescriptor : descriptors) {
            if (valueDescriptor.getName().equals(entry.name)) {
                if (entry instanceof SimpleConfigEntry) {
                    assertThat(((SimpleConfigEntry) entry).value).isEqualTo(valueDescriptor.getValue());
                    return;
                }
                List<ConfigEntry> entries = ((ComplexConfigEntry) entry).entries;
                for (ConfigEntry subEntry : entries) {
                    validateConfigurationEntry((List<ValueDescriptor<?>>) valueDescriptor.getValue(), subEntry);
                }
                return;
            }
        }
        Assert.fail("Configuration entry not found: " + entry.name);
    }

    private MavenPluginDescriptor validatePlugin(List<MavenPluginDescriptor> pluginDescriptors, Plugin plugin) {
        for (MavenPluginDescriptor pluginDescriptor : pluginDescriptors) {
            MavenArtifactDescriptor artifact = pluginDescriptor.getArtifact();
            assertThat(artifact).isNotNull();
            if (Objects.equals(artifact.getClassifier(), plugin.classifier) && //
                    Objects.equals(artifact.getGroup(), plugin.group) && //
                    Objects.equals(artifact.getName(), plugin.name) && //
                    Objects.equals(artifact.getType(), "jar") && //
                    Objects.equals(artifact.getVersion(), plugin.version) && //
                    Objects.equals(pluginDescriptor.isInherited(), plugin.inherited)) {
                return pluginDescriptor;
            }
        }
        Assert.fail("Plugin not found: " + plugin.toString());
        return null;
    }

    private MavenPluginExecutionDescriptor validatePluginExecution(List<MavenPluginExecutionDescriptor> executionDescriptors, Execution execution) {
        for (MavenPluginExecutionDescriptor mavenPluginExecutionDescriptor : executionDescriptors) {
            if (Objects.equals(mavenPluginExecutionDescriptor.getId(), execution.id) && //
                    Objects.equals(mavenPluginExecutionDescriptor.getPhase(), execution.phase) && //
                    Objects.equals(mavenPluginExecutionDescriptor.isInherited(), execution.inherited)) {
                return mavenPluginExecutionDescriptor;
            }
        }

        Assert.fail("Execution not found: " + execution.toString());
        return null;
    }

    private List<Dependency> createChildDependencies() {
        List<Dependency> dependencyList = new ArrayList<>();
        dependencyList.add(createDependency("com.buschmais.jqassistant.core", "jqassistant.core.analysis", "test-jar", "${project.version}", null, "test"));
        dependencyList.add(createDependency("com.buschmais.jqassistant.core", "jqassistant.core.store", "jar", null, null, null));
        dependencyList.add(createDependency("junit", "junit", "jar", null, null, null));
        dependencyList.add(createDependency("org.slf4j", "slf4j-simple", "jar", null, null, null));
        return dependencyList;
    }

    private List<Dependency> getExpectedManagedParentDependencies() {
        List<Dependency> dependencyList = new ArrayList<>();
        dependencyList.add(createDependency("com.buschmais.jqassistant.core", "jqassistant.core.store", "jar", "${project.version}", null, null));
        dependencyList.add(createDependency("junit", "junit", "jar", "4.11", null, "test"));
        dependencyList.add(createDependency("org.slf4j", "slf4j-simple", "jar", "${org.slf4j_version}", null, "test"));
        return dependencyList;
    }

    private Dependency createDependency(String group, String artifact, String type, String version, String classifier, String scope) {
        Dependency dependency = new Dependency(group, artifact, version);
        dependency.type = type;
        dependency.classifier = classifier;
        dependency.scope = scope;
        return dependency;
    }

    private Plugin createPlugin(String group, String artifact, String type, String version, String classifier, boolean inherited) {
        Plugin plugin = new Plugin(group, artifact, version, type);
        plugin.classifier = classifier;
        plugin.inherited = inherited;
        return plugin;
    }

    private List<Plugin> createParentPlugins() {
        List<Plugin> pluginList = new ArrayList<>();
        pluginList.add(createPlugin("org.apache.maven.plugins", "maven-compiler-plugin", null, "3.3", null, true));
        Plugin javadocPlugin = createPlugin("org.apache.maven.plugins", "maven-javadoc-plugin", null, "2.10.1", null, true);
        Execution attachJavadocExecution = createPluginExecution("attach-javadoc", null, true);
        attachJavadocExecution.goals.add("jar");
        javadocPlugin.executions.add(attachJavadocExecution);
        pluginList.add(javadocPlugin);

        Plugin sourceplugin = createPlugin("org.apache.maven.plugins", "maven-source-plugin", null, "2.2.1", null, true);
        Execution attachSourcesExecution = createPluginExecution("attach-sources", null, true);
        attachSourcesExecution.goals.add("jar-no-fork");
        sourceplugin.executions.add(attachSourcesExecution);
        pluginList.add(sourceplugin);

        Plugin sitePlugin = createPlugin("com.github.github", "site-maven-plugin", null, "0.10", null, true);
        Execution siteExecution = createPluginExecution("github-site", "site-deploy", true);
        sitePlugin.executions.add(siteExecution);
        siteExecution.goals.add("site");
        Configuration executionConf = new Configuration();
        siteExecution.configuration = executionConf;
        executionConf.entries.add(new SimpleConfigEntry("testparam1", "test1"));
        ComplexConfigEntry executionParams = new ComplexConfigEntry("paramlist");
        executionConf.entries.add(executionParams);
        executionParams.entries.add(new SimpleConfigEntry("testparam2", "test2"));
        executionParams.entries.add(new SimpleConfigEntry("testparam3", "test3"));
        Configuration pluginConf = new Configuration();
        sitePlugin.configuration = pluginConf;
        pluginConf.entries.add(new SimpleConfigEntry("message", "Creating site for ${project.artifactId}, ${project.version}"));
        pluginConf.entries.add(new SimpleConfigEntry("path", "${project.distributionManagement.site.url}"));
        pluginConf.entries.add(new SimpleConfigEntry("merge", "true"));
        ComplexConfigEntry entry = new ComplexConfigEntry("paramlist");
        pluginConf.entries.add(entry);
        entry.entries.add(new SimpleConfigEntry("testparam4", "test4"));
        pluginList.add(sitePlugin);

        return pluginList;
    }

    private List<Profile> createParentProfiles() {
        List<Profile> profiles = new ArrayList<>();
        Profile itProfile = new Profile("IT");
        profiles.add(itProfile);
        itProfile.dependencies.add(new Dependency("dummyGroup", "dummyArtifact", "dummyVersion"));
        itProfile.managedDependencies.add(new Dependency("dummyManagedGroup", "dummyManagedArtifact", "dummyManagedVersion"));
        // plugin
        Plugin failsafePlugin = new Plugin("org.apache.maven.plugins", "maven-failsafe-plugin", null, null);
        itProfile.plugins.add(failsafePlugin);
        Execution failsafeExecution = new Execution();
        failsafePlugin.executions.add(failsafeExecution);
        failsafeExecution.id = "default";
        failsafeExecution.goals.add("integration-test");
        failsafeExecution.goals.add("verify");
        Configuration failsafeConfig = new Configuration();
        failsafeExecution.configuration = failsafeConfig;
        failsafeConfig.entries.add(new SimpleConfigEntry("argLine", "-Xmx512M"));
        failsafeConfig.entries.add(new SimpleConfigEntry("forkCount", "1"));
        failsafeConfig.entries.add(new SimpleConfigEntry("reuseForks", "true"));
        // managed plugin
        Plugin managedPlugin = new Plugin("org.apache.maven.plugins", "maven-failsafe-plugin", "unknownVersion", null);
        managedPlugin.inherited = false;
        itProfile.managedPlugins.add(managedPlugin);
        // modules
        itProfile.modules.add("childModule1");
        itProfile.modules.add("childModule2");
        // profiles
        itProfile.properties.put("testProperty1", "testValue");
        itProfile.properties.put("testProperty2", "anotherTestValue");
        // activation
        ProfileActivation activation = new ProfileActivation();
        itProfile.activation = activation;
        activation.activeByDefault = true;
        activation.jdk = "1.8";
        activation.propertyName = "activationProperty";
        activation.propertyValue = "activationPropertyValue";
        activation.fileExists = "activate.xml";
        activation.fileMissing = "deactivate.xml";
        activation.osArch = "x86";
        activation.osFamily = "Windows";
        activation.osName = "Windows XP";
        activation.osVersion = "5.1.2600";
        return profiles;
    }

    private List<Plugin> createManagedParentPlugins() {
        List<Plugin> pluginList = new ArrayList<>();
        Plugin enforcerPlugin = createPlugin("org.apache.maven.plugins", "maven-enforcer-plugin", null, "1.0", null, true);
        Execution enforcerExecution = createPluginExecution("enforce-maven", "validate", true);
        enforcerExecution.goals.add("enforce");
        enforcerPlugin.executions.add(enforcerExecution);
        pluginList.add(enforcerPlugin);

        Plugin jaxbPlugin = createPlugin("org.jvnet.jaxb2.maven2", "maven-jaxb2-plugin", null, "0.9.0", null, true);
        Execution jaxbDefaultExecution = createPluginExecution("default", null, true);
        jaxbDefaultExecution.goals.add("generate");
        jaxbPlugin.executions.add(jaxbDefaultExecution);
        Configuration config = new Configuration();
        config.entries.add(new SimpleConfigEntry("schemaDirectory", "src/main/resources/META-INF/xsd"));
        ComplexConfigEntry argsEntry = new ComplexConfigEntry("args");
        argsEntry.entries.add(new SimpleConfigEntry("arg", "-Xdefault-value"));
        config.entries.add(argsEntry);
        ComplexConfigEntry pluginsEntry = new ComplexConfigEntry("plugins");
        ComplexConfigEntry pluginEntry = new ComplexConfigEntry("plugin");
        pluginEntry.entries.add(new SimpleConfigEntry("version", "1.1"));
        pluginEntry.entries.add(new SimpleConfigEntry("groupId", "org.jvnet.jaxb2_commons"));
        pluginEntry.entries.add(new SimpleConfigEntry("artifactId", "jaxb2-default-value"));
        pluginsEntry.entries.add(pluginEntry);
        config.entries.add(pluginsEntry);
        jaxbDefaultExecution.configuration = config;
        pluginList.add(jaxbPlugin);

        Plugin jqaMavenPlugin = createPlugin("com.buschmais.jqassistant.scm", "jqassistant-maven-plugin", null, "${project.version}", null, true);
        Execution scanExecution = createPluginExecution("scan", null, true);
        scanExecution.goals.add("scan");
        jqaMavenPlugin.executions.add(scanExecution);
        Execution analyzeExecution = createPluginExecution("analyze", null, true);
        analyzeExecution.goals.add("analyze");
        jqaMavenPlugin.executions.add(analyzeExecution);
        pluginList.add(jqaMavenPlugin);

        pluginList.add(createPlugin("org.apache.maven.plugins", "maven-failsafe-plugin", null, "2.18", null, true));
        Plugin surefirePlugin = createPlugin("org.apache.maven.plugins", "maven-surefire-plugin", null, "2.18", null, true);
        Configuration surefireConfig = new Configuration();
        ComplexConfigEntry includesEntry = new ComplexConfigEntry("includes");
        includesEntry.entries.add(new SimpleConfigEntry("include", "**/*Test.java"));
        surefireConfig.entries.add(includesEntry);
        surefirePlugin.configuration = surefireConfig;
        pluginList.add(surefirePlugin);

        Plugin assemblyPlugin = createPlugin("org.apache.maven.plugins", "maven-assembly-plugin", null, "2.5", null, true);
        Execution asciidocExecution = createPluginExecution("attach-asciidoc", "", true);
        asciidocExecution.goals.add("single");
        assemblyPlugin.executions.add(asciidocExecution);
        Execution distributionExecution = createPluginExecution("attach-distribution", null, true);
        distributionExecution.goals.add("single");
        assemblyPlugin.executions.add(distributionExecution);
        pluginList.add(assemblyPlugin);

        pluginList.add(createPlugin("org.apache.maven.plugins", "maven-jar-plugin", null, "2.4", null, true));
        pluginList.add(createPlugin("org.apache.maven.plugins", "maven-site-plugin", null, "3.3", null, true));

        return pluginList;
    }

    private Execution createPluginExecution(String id, String phase, boolean inherited) {
        Execution execution = new Execution();
        execution.id = id;
        execution.phase = phase;
        execution.inherited = inherited;
        return execution;
    }

    private class Profile {
        private String id;
        private List<String> modules = new ArrayList<>();
        private List<Plugin> plugins = new ArrayList<>();
        private List<Plugin> managedPlugins = new ArrayList<>();
        private List<Dependency> managedDependencies = new ArrayList<>();
        private List<Dependency> dependencies = new ArrayList<>();
        private Properties properties = new Properties();
        private ProfileActivation activation;

        private Profile(String id) {
            this.id = id;
        }
    }

    private class ProfileActivation {
        private boolean activeByDefault = false;
        private String jdk;
        private String propertyName, propertyValue;
        private String fileExists, fileMissing;
        private String osArch, osFamily, osName, osVersion;
    }

    private class Artifact {
        protected String group, name, version, type, classifier;

        private Artifact(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }
    }

    private class Dependency extends Artifact {
        private String scope;

        private Dependency(String group, String artifact, String version) {
            super(group, artifact, version);
            super.type = "jar";
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return String.format("%s:%s:%s:%s:%s:%s", group, name, type, version, classifier, scope);
        }
    }

    private class Plugin extends Artifact {
        private boolean inherited = true;
        private Configuration configuration;
        private List<Execution> executions = new ArrayList<>();

        private Plugin(String group, String artifact, String version, String type) {
            super(group, artifact, version);
            super.type = type;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return String.format("%s:%s:%s:%s:%s:%s", group, name, type, version, classifier, inherited);
        }
    }

    private class Execution {
        private String id, phase;
        private boolean inherited = true;
        private Configuration configuration;
        private List<String> goals = new ArrayList<>();

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return String.format("%s:%s:%s", id, phase, inherited);
        }
    }

    private class Configuration {
        private List<ConfigEntry> entries = new ArrayList<>();
    }

    private class ConfigEntry {
        private ConfigEntry(String name) {
            this.name = name;
        }

        protected String name;
    }

    private class SimpleConfigEntry extends ConfigEntry {
        private SimpleConfigEntry(String name, String value) {
            super(name);
            this.value = value;
        }

        private String value;
    }

    private class ComplexConfigEntry extends ConfigEntry {
        private ComplexConfigEntry(String name) {
            super(name);
        }

        private List<ConfigEntry> entries = new ArrayList<>();
    }
}
