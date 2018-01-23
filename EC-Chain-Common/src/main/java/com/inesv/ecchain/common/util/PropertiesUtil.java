package com.inesv.ecchain.common.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class PropertiesUtil {
    private static final Properties properties = new Properties();

    public static Properties getProperties() {
        try {
            properties.load(PropertiesUtil.class.getClassLoader().getResourceAsStream("ec.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }

    public static boolean getKeyForBoolean(String key) {
        String value = properties.getProperty(key);
        if (Boolean.TRUE.toString().equals(value)) {
            return true;
        }
        return false;
    }

    public static int getKeyForInt(String key, int defaults) {
        try {
            String value = properties.getProperty(key);
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return defaults;
        }
    }

    public static List<String> getStringListProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.length() == 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String s : value.split(";")) {
            s = s.trim();
            if (s.length() > 0) {
                result.add(s);
            }
        }
        return result;
    }

    public static String getKeyForString(String key, String defaults) {
        String value = properties.getProperty(key);
        if (value == null || "".equals(value)) {
            return defaults;
        }
        return value;
    }
}
