package io.quarkiverse.tekton.cm;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;

public class MavenSettingsCm {
    private static final String SETTINGS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <settings>
              <mirrors>
                <mirror>
                  <id>maven.org</id>
                  <name>Default mirror</name>
                  <url>https://repo1.maven.org/maven2</url>
                  <mirrorOf>central</mirrorOf>
                </mirror>
              </mirrors>
            </settings>
            """;

    public static ConfigMap create(String name) {
        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .addToData("settings.xml", SETTINGS_XML)
                .build();
    }
}
