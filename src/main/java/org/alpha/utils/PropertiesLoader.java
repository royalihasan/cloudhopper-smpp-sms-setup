package org.alpha.utils;


import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Properties;

public class PropertiesLoader {
    private static final String PROPERTIES_FILE = "application.properties";
    private static Properties properties;

    static {
        try (InputStream input = PropertiesLoader.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new IOException("Unable to find " + PROPERTIES_FILE);
            }
            properties = new Properties();
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file", e);
        }
    }

    /**
     * Retrieves a property value and attempts to convert it to the appropriate type.
     *
     * @param <T>   The type of the value to retrieve.
     * @param key   The property key.
     * @param clazz The class type of the value.
     * @return The converted value of the specified type.
     */
    public static <T> T get(String key, Class<T> clazz) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Property not found: " + key);
        }

        if (clazz == String.class) {
            return clazz.cast(value);
        }

        if (clazz == Integer.class) {
            return clazz.cast(Integer.valueOf(value));
        }

        if (clazz == Long.class) {
            return clazz.cast(Long.valueOf(value));
        }

        if (clazz == Boolean.class) {
            return clazz.cast(Boolean.valueOf(value));
        }

        if (clazz == Double.class) {
            return clazz.cast(Double.valueOf(value));
        }

        // Add more type conversions if needed
        throw new UnsupportedOperationException("Unsupported type: " + clazz.getName());
    }
}
