package safepath;

import java.util.*;

public class CrimeDataGenerator {
    public static class CrimeEvent {
        public double lat;
        public double lon;
        public String type;
        public int severity; // 1 to 5 (5 is most severe)
        public String timeOfDay; // Morning, Afternoon, Evening, Night

        public CrimeEvent(double lat, double lon, String type, int severity, String timeOfDay) {
            this.lat = lat;
            this.lon = lon;
            this.type = type;
            this.severity = severity;
            this.timeOfDay = timeOfDay;
        }
    }

    private static final String[] CRIME_TYPES = {
        "Harassment", "Pickpocketing", "Snatching", "Physical Assault", "Stalking"
    };

    private static final String[] TIMES_OF_DAY = {
        "Morning", "Afternoon", "Evening", "Night"
    };

    public static List<CrimeEvent> generateSimulatedCrimes(double centerLat, double centerLon, int count) {
        List<CrimeEvent> crimes = new ArrayList<>();
        Random random = new Random(42); // Seeded for reproducibility

        double latDegreePerMeter = 1.0 / 111320.0;
        double lonDegreePerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(centerLat)));

        // Create 4 distinct high-crime hotspots procedural centers (e.g. dark alleys, crowded markets, unlit parks)
        double[][] hotspots = {
            {centerLat + 400 * latDegreePerMeter, centerLon - 300 * lonDegreePerMeter, 4.5}, // Market snatching zone
            {centerLat - 600 * latDegreePerMeter, centerLon + 500 * lonDegreePerMeter, 3.8}, // Remote park harassment zone
            {centerLat + 200 * latDegreePerMeter, centerLon + 700 * lonDegreePerMeter, 4.2}, // Unlit metro station area
            {centerLat - 800 * latDegreePerMeter, centerLon - 600 * lonDegreePerMeter, 5.0}  // Alley assault zone
        };

        for (int i = 0; i < count; i++) {
            double lat, lon;
            int severity;
            String type = CRIME_TYPES[random.nextInt(CRIME_TYPES.length)];
            String time = TIMES_OF_DAY[random.nextInt(TIMES_OF_DAY.length)];

            // 75% of crimes occur within the predefined hotspots, 25% are background noise
            if (random.nextDouble() < 0.75) {
                double[] hotspot = hotspots[random.nextInt(hotspots.length)];
                // Standard distribution around the hotspot center (with some deviation)
                double radiusOffset = random.nextGaussian() * 150.0; // 150m standard deviation
                double angle = random.nextDouble() * 2.0 * Math.PI;

                lat = hotspot[0] + (radiusOffset * Math.cos(angle)) * latDegreePerMeter;
                lon = hotspot[1] + (radiusOffset * Math.sin(angle)) * lonDegreePerMeter;
                
                // Severity slightly higher near hotspot center
                severity = (int) Math.round(hotspot[2] - (random.nextDouble() * 2));
                severity = Math.max(1, Math.min(5, severity));
            } else {
                // Background noise: random within 1.5km
                double radiusOffset = random.nextDouble() * 1500.0;
                double angle = random.nextDouble() * 2.0 * Math.PI;

                lat = centerLat + (radiusOffset * Math.cos(angle)) * latDegreePerMeter;
                lon = centerLon + (radiusOffset * Math.sin(angle)) * lonDegreePerMeter;
                severity = random.nextInt(3) + 1;
            }

            // Adjust times: crimes are more severe or frequent at night
            if (time.equals("Night")) {
                severity = Math.min(5, severity + 1);
            }

            crimes.add(new CrimeEvent(lat, lon, type, severity, time));
        }

        return crimes;
    }
}
