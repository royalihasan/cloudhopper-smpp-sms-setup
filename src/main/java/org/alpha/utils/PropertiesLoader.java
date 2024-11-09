package org.alpha.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesLoader {
    private static Properties properties;
    private static String propertiesFile = "application.properties"; // default value

    /**
     * Initialize the properties loader with a specific properties file
     * @param fileName name of the properties file in the classpath
     */
    public static void init(String fileName) {
        propertiesFile = fileName;
        loadProperties();
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = PropertiesLoader.class.getClassLoader().getResourceAsStream(propertiesFile)) {
            if (input == null) {
                throw new IOException("Unable to find " + propertiesFile);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file: " + propertiesFile, e);
        }
    }

    static {
        loadProperties(); // Load default properties file if init() is not called
    }

    /**
     * Static inner class for accessing properties
     */
    public static class properties {
        public static final int port = get("smpp.server.port", Integer.class);
        public static final int maxConnectionSize = get("smpp.server.maxConnectionSize", Integer.class);
        public static final long defaultRequestExpiryTimeout = get("smpp.server.defaultRequestExpiryTimeout", Long.class);
        public static final long defaultWindowMonitorInterval = get("smpp.server.defaultWindowMonitorInterval", Long.class);
        public static final int defaultWindowSize = get("smpp.server.defaultWindowSize", Integer.class);
        public static final long defaultWindowWaitTimeout = get("smpp.server.defaultWindowWaitTimeout", Long.class);
        public static final boolean nonBlockingSocketsEnabled = get("smpp.server.nonBlockingSocketsEnabled", Boolean.class);
        public static final boolean sessionCountersEnabled = get("smpp.server.sessionCountersEnabled", Boolean.class);
        public static final boolean jmxEnabled = get("smpp.server.jmxEnabled", Boolean.class);
        public static final String clientHost = get("smpp.client.host", String.class);
        public static final int clientPort = get("smpp.client.port", Integer.class);
        public static final String clientSystemId = get("smpp.client.systemId", String.class);
        public static final String clientPassword = get("smpp.client.password", String.class);
        public static final int clientConnectTimeout = get("smpp.client.connectTimeout", Integer.class);
        public static final long clientRequestExpiryTimeout = get("smpp.client.requestExpiryTimeout", Long.class);
        public static final long clientWindowMonitorInterval = get("smpp.client.windowMonitorInterval", Long.class);
        public static final long clientEnquireLinkTimeout = get("smpp.client.enquireLinkTimeout", Long.class);
        public static final int clientSubmitTimeout = get("smpp.client.submitTimeout", Integer.class);
        public static final int clientUnbindTimeout = get("smpp.client.unbindTimeout", Integer.class);
    }

    /**
     * Retrieves a property value and converts it to the specified type.
     *
     * @param key   The property key.
     * @param clazz The expected class type of the value.
     * @param <T>   The type of the value.
     * @return The property value, converted to the appropriate type.
     * @throws IllegalArgumentException if the property is not found or cannot be converted
     */
    private static <T> T get(String key, Class<T> clazz) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Property not found: " + key);
        }

        try {
            if (clazz == Boolean.class) {
                return clazz.cast(Boolean.parseBoolean(value));
            } else if (clazz == Integer.class) {
                return clazz.cast(Integer.parseInt(value));
            } else if (clazz == Long.class) {
                return clazz.cast(Long.parseLong(value));
            } else if (clazz == Double.class) {
                return clazz.cast(Double.parseDouble(value));
            } else {
                return clazz.cast(value);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for property " + key + ": " + value, e);
        }
    }
}