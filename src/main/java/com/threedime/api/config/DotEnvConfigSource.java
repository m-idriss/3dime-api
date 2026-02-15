package com.threedime.api.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DotEnvConfigSource implements ConfigSource {

    private final Map<String, String> properties;

    public DotEnvConfigSource() {
        Map<String, String> loadedProperties = new HashMap<>();
        try {
            // Load .env file, ignoring if missing
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            for (io.github.cdimascio.dotenv.DotenvEntry entry : dotenv.entries()) {
                loadedProperties.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            // Ignore errors loading .env
        }
        this.properties = Collections.unmodifiableMap(loadedProperties);
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return "DotEnvConfigSource";
    }

    @Override
    public int getOrdinal() {
        // Use ordinal 290 so .env overrides application.properties (100)
        // but remains below environment variables (300) and system properties (400).
        return 290;
    }
}
