package com.buschmais.jqassistant.plugin.maven3.test.scanner;

import java.io.File;
import java.util.List;

import com.buschmais.jqassistant.plugin.java.test.AbstractJavaPluginIT;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenPluginDescriptor;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenPomXmlDescriptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SingleConfiguredPluginIT extends AbstractJavaPluginIT {

    @BeforeEach
    void setUp() throws Exception {
        File rootDirectory = getClassesDirectory(SingleConfiguredPluginIT.class);
        File projectDirectory = new File(rootDirectory, "plugin/single-configured-plugin");
        scanClassPathDirectory(projectDirectory);

        store.beginTransaction();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (store.hasActiveTransaction()) {
            store.commitTransaction();
        }
    }

    @Test
    void declaredPluginCanBeFoundThroughRelationShip() {
        // There should be one declared plugin
        List<MavenPluginDescriptor> pluginDescriptors =
            query("MATCH (p:Maven:Pom)-[:USES_PLUGIN]->(n:Maven:Plugin) RETURN n").getColumn("n");

        assertThat(pluginDescriptors).hasSize(1);
    }

    @Test
    void declaredPluginCanBeFound() throws Exception {
        // There should be one Maven Project
        List<MavenPomXmlDescriptor> mavenPomDescriptors =
            query("MATCH (n:File:Maven:Xml:Pom) WHERE n.fileName='/pom.xml' RETURN n").getColumn("n");

        assertThat(mavenPomDescriptors).hasSize(1);
        assertThat(mavenPomDescriptors.get(0).getArtifactId()).isEqualTo("single-configured-plugin");

        // There should be one declared plugin
        List<MavenPluginDescriptor> pluginDescriptors =
            query("MATCH (n:Maven:Plugin) RETURN n").getColumn("n");

        assertThat(pluginDescriptors).hasSize(1);
    }

    @Test
    void allPropertiesOfTheDeclaredPluginAreFound() {
        List<MavenPomXmlDescriptor> mavenPomDescriptors =
            query("MATCH (n:File:Maven:Xml:Pom) WHERE n.fileName='/pom.xml' RETURN n").getColumn("n");

        assertThat(mavenPomDescriptors).hasSize(1);
        assertThat(mavenPomDescriptors.get(0).getArtifactId()).isEqualTo("single-configured-plugin");

        // There should be one declared plugin
        List<Boolean> inherited = query("MATCH (n:Maven:Plugin) RETURN n.inherited AS i").getColumn("i");

        assertThat(inherited).containsExactlyInAnyOrder(Boolean.FALSE);
    }
}
