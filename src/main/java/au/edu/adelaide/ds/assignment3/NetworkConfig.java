package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.util.*;

/**
 * Parses and provides access to the network configuration for all Paxos members.
 * <p>
 * This class reads a file named {@code network.config} from the classpath. The file must contain
 * one line per node in the format: {@code MemberID,Host,Port}.
 * </p>
 * <p>
 * Example line: {@code M1,localhost,9001}
 * </p>
 */
public class NetworkConfig {
    private final Map<String, String> memberHosts = new HashMap<>();
    private final Map<String, Integer> memberPorts = new HashMap<>();

    /**
     * Loads the {@code network.config} file from the classpath and parses each line
     * into member host and port mappings.
     *
     * @return a fully populated {@code NetworkConfig} instance
     * @throws IOException if the file is missing or cannot be read properly
     */
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

    /**
     * Returns the hostname for the given member ID.
     *
     * @param memberId the ID of the member (e.g., "M3")
     * @return the hostname (e.g., "localhost")
     */
    public String getHost(String memberId) {
        return memberHosts.getOrDefault(memberId, "localhost");
    }

    /**
     * Returns the port number for the given member ID.
     *
     * @param memberId the ID of the member (e.g., "M2")
     * @return the port number
     * @throws IllegalArgumentException if no port is mapped for the given member ID
     */
    public int getPort(String memberId) {
        Integer p = memberPorts.get(memberId);
        if (p == null) {
            throw new IllegalArgumentException("No port found for member ID: " + memberId);
        }
        return p;
    }

    /**
     * Returns a set of all member IDs defined in the configuration.
     *
     * @return a {@code Set<String>} of member IDs
     */
    public Set<String> getAllMembers() {
        return memberPorts.keySet();
    }

    /**
     * Returns the complete mapping of member IDs to their ports.
     *
     * @return a {@code Map<String, Integer>} of member ID to port number
     */
    public Map<String, Integer> getMemberPorts() {
        return memberPorts;
    }

    /**
     * Returns the complete mapping of member IDs to their hostnames.
     *
     * @return a {@code Map<String, String>} of member ID to hostname
     */
    public Map<String, String> getMemberHosts() {
        return memberHosts;
    }
}
