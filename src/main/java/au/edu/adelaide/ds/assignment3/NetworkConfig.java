package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.util.*;

public class NetworkConfig {
    private final Map<String, String> memberHosts = new HashMap<>();
    private final Map<String, Integer> memberPorts = new HashMap<>();

    public static NetworkConfig load() throws IOException {
        NetworkConfig config = new NetworkConfig();
        InputStream inputStream = NetworkConfig.class.getClassLoader().getResourceAsStream("network.config");

        if (inputStream == null) {
            throw new FileNotFoundException("network.config not found in classpath!");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue; // skip comments or blank lines
                String[] parts = line.split(",");
                if (parts.length == 3) {
                    String memberId = parts[0].trim();
                    String host = parts[1].trim();
                    int port = Integer.parseInt(parts[2].trim());

                    config.memberHosts.put(memberId, host);
                    config.memberPorts.put(memberId, port);
                }
            }
        }

        return config;
    }

    public String getHost(String memberId) {
        return memberHosts.getOrDefault(memberId, "localhost");
    }

    public int getPort(String memberId) {
        Integer p = memberPorts.get(memberId);
        if (p == null) {
            throw new IllegalArgumentException("No port found for member ID: " + memberId);
        }
        return p;
    }

    public Set<String> getAllMembers() {
        return memberPorts.keySet();
    }

    public Map<String, Integer> getMemberPorts() {
        return memberPorts;
    }

    public Map<String, String> getMemberHosts() {
        return memberHosts;
    }
}
