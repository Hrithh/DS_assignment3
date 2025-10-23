package au.edu.adelaide.ds.assignment3;

import java.util.Random;

public enum Profile {
    RELIABLE,
    STANDARD,
    LATENT,
    FAILURE;

    private static final Random rand = new Random();

    public static Profile fromString(String str) {
        switch (str.toLowerCase()) {
            case "reliable": return RELIABLE;
            case "standard": return STANDARD;
            case "latent": return LATENT;
            case "failure": return FAILURE;
            default: throw new IllegalArgumentException("Unknown profile: " + str);
        }
    }

    public void simulateNetworkDelay() {
        try {
            switch (this) {
                case RELIABLE:
                    break;
                case STANDARD:
                    Thread.sleep(50 + rand.nextInt(100));
                    break;
                case LATENT:
                    Thread.sleep(500 + rand.nextInt(1000));
                    break;
                case FAILURE:
                    Thread.sleep(100 + rand.nextInt(200));
                    break;
            }
        } catch (InterruptedException ignored) {}
    }

    public boolean shouldDrop() {
        if (this == FAILURE) {
            return rand.nextDouble() < 0.2;
        }
        return false;
    }
}
