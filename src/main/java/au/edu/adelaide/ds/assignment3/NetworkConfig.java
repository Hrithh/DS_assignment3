package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

public class NetworkConfig {

    private final Map<String, InetSocketAddress> members = new HashMap<>();

    public static NetworkConfig load() throws IOException {
        NetworkConfig cfg = new NetworkConfig();

        // Try classpath first (src/main/resources), then project root as fallback.
        InputStream in = NetworkConfig.class.getClassLoader().getResourceAsStream("network.config");
        if (in == null) {
            File f = new File("network.config");
            if (!f.exists()) {
                throw new FileNotFoundException(
                        "network.config not found. Place it in src/main/resources or project root: " +
                                new File(".").getAbsolutePath());
            }
            in = new FileInputStream(f);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            int lineno = 0;
            while ((line = br.readLine()) != null) {
                lineno++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",");
                if (parts.length != 3) {
                    throw new IOException("Invalid line " + lineno + " in network.config: " + line);
                }

                String id   = parts[0].trim();
                String host = parts[1].trim();
                String portStr = parts[2].trim();
                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid port on line " + lineno + ": " + portStr);
                }

                cfg.members.put(id, new InetSocketAddress(host, port));
            }
        }

        if (cfg.members.isEmpty()) {
            throw new IOException("network.config parsed but no members found.");
        }

        return cfg;
    }

    public InetSocketAddress getAddress(String memberId) {
        InetSocketAddress addr = members.get(memberId);
        if (addr == null) {
            throw new IllegalArgumentException("Member '" + memberId + "' not in network.config. Known: " + members.keySet());
        }
        return addr;
    }

    public String getHost(String memberId) {
        return getAddress(memberId).getHostString();
    }

    public int getPort(String memberId) {
        return getAddress(memberId).getPort();
    }

    public Set<String> getAllMembers() {
        return Collections.unmodifiableSet(members.keySet());
    }
}
