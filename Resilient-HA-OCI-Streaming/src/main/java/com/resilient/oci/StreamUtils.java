package com.resilient.oci;

import java.io.*;
import java.util.Properties;

public class StreamUtils {

    private static final String ABS_PATH = "C:\\Users\\Silvio\\genai\\gs-rest-service-initial2\\src\\main\\resources\\stream.properties";
    private static final String KEY_ENDPOINT = "SECONDARY_STREAM_ENDPOINT";
    private static final String KEY_OCID = "SECONDARY_STREAM_OCID";

    public static void saveSecondaryStream(String streamOcid, String endpoint) {
        Properties p = new Properties();
        p.setProperty(KEY_OCID, streamOcid == null ? "" : streamOcid);
        p.setProperty(KEY_ENDPOINT, endpoint == null ? "" : endpoint);
        try (FileOutputStream fos = new FileOutputStream(ABS_PATH)) {
            p.store(fos, "Secondary stream info (OCID + endpoint)");
            System.out.println("Saved secondary stream info to " + ABS_PATH);
        } catch (IOException e) {
            System.err.println("Error saving secondary stream info: " + e.getMessage());
        }
    }

    public static String loadSecondaryStreamEndpoint() {
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(ABS_PATH)) {
            p.load(fis);
            String v = p.getProperty(KEY_ENDPOINT);
            return (v == null || v.isBlank()) ? null : v.trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static String loadSecondaryStreamOcid() {
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(ABS_PATH)) {
            p.load(fis);
            String v = p.getProperty(KEY_OCID);
            return (v == null || v.isBlank()) ? null : v.trim();
        } catch (IOException e) {
            return null;
        }
    }
    

    public static void exportEnv(String key, String value) {
        if (key == null || value == null) return;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "setx", key, value).inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("bash", "-lc", "export " + key + "=\"" + value + "\"").inheritIO().start().waitFor();
            }
            System.out.println("Exported env " + key + "=" + value);
        } catch (Exception ex) {
            System.err.println("Failed to export env var: " + ex.getMessage());
        }
    }
}
