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
        // Priority higher than application.properties (which is usually around 250-260)
        // but lower than System Properties (400) and Env Vars (300).
        // Let's verify standard ordinal.
        // MicroProfile Config spec:
        // System.getProperties() = 400
        // System.getenv() = 300
        // application.properties = 100

        // We want .env to be treated like local environment variables, so maybe 290?
        // Or if we want it to override application.properties defaults, > 100 is
        // enough.
        return 290;
    }
}
