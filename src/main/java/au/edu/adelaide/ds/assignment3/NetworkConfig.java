package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.util.*;

public class NetworkConfig {
    private final Map<String, Integer> memberPorts = new HashMap<>();

    public static NetworkConfig load(String path) throws IOException {
        NetworkConfig config = new NetworkConfig();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split(",");
            if (parts.length == 3) {
                config.memberPorts.put(parts[0], Integer.parseInt(parts[2]));
            }
        }
        return config;
    }

    public int getPort(String memberId) {
        return memberPorts.get(memberId);
    }

    public Set<String> getAllMembers() {
        return memberPorts.keySet();
    }

    public String getHostPort(String memberId) {
        return "localhost:" + getPort(memberId);
    }

    public Map<String, Integer> getMemberPorts() {
        return memberPorts;
    }
}
