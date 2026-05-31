package safepath;

import java.util.*;

public class KMeansClustering {
    public static class Cluster {
        public double lat;
        public double lon;
        public double radiusMeters; // Coverage radius of the crime hotspot
        public double intensity; // Total weighted severity of crimes in this cluster
        public int crimeCount;
        public List<CrimeDataGenerator.CrimeEvent> members = new ArrayList<>();

        public Cluster(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        public void recalculateCenter() {
            if (members.isEmpty()) return;
            double sumLat = 0;
            double sumLon = 0;
            double totalWeight = 0;
            for (CrimeDataGenerator.CrimeEvent event : members) {
                // Weigh more severe crimes higher in center computation
                double weight = event.severity;
                sumLat += event.lat * weight;
                sumLon += event.lon * weight;
                totalWeight += weight;
            }
            this.lat = sumLat / totalWeight;
            this.lon = sumLon / totalWeight;
        }

        public void calculateRadiusAndIntensity() {
            if (members.isEmpty()) {
                this.radiusMeters = 0;
                this.intensity = 0;
                this.crimeCount = 0;
                return;
            }

            this.crimeCount = members.size();
            double weightedSeveritySum = 0;
            List<Double> distances = new ArrayList<>();

            for (CrimeDataGenerator.CrimeEvent event : members) {
                double dist = Graph.haversine(this.lat, this.lon, event.lat, event.lon);
                distances.add(dist);
                weightedSeveritySum += event.severity;
            }

            // Radius is set as the 80th percentile of distance to avoid outliers blowing up the zone
            Collections.sort(distances);
            int percentileIndex = (int) (distances.size() * 0.80);
            this.radiusMeters = Math.max(100.0, distances.get(percentileIndex)); // minimum 100m radius

            // Intensity normalized relative to count and severity
            this.intensity = weightedSeveritySum;
        }
    }

    public static List<Cluster> clusterCrimes(List<CrimeDataGenerator.CrimeEvent> crimes, int k, int maxIterations) {
        if (crimes.isEmpty() || k <= 0) return new ArrayList<>();

        List<Cluster> clusters = new ArrayList<>();
        Random random = new Random(101);

        // --- K-Means++ Initialization ---
        // 1. Choose first center uniformly at random
        CrimeDataGenerator.CrimeEvent firstCenter = crimes.get(random.nextInt(crimes.size()));
        clusters.add(new Cluster(firstCenter.lat, firstCenter.lon));

        // 2. Select remaining K-1 centers using probability proportional to D(x)^2
        while (clusters.size() < k) {
            double[] dSquared = new double[crimes.size()];
            double sumDSquared = 0;

            for (int i = 0; i < crimes.size(); i++) {
                CrimeDataGenerator.CrimeEvent crime = crimes.get(i);
                double minDistance = Double.MAX_VALUE;

                // Find closest existing center
                for (Cluster c : clusters) {
                    double dist = Graph.haversine(crime.lat, crime.lon, c.lat, c.lon);
                    if (dist < minDistance) {
                        minDistance = dist;
                    }
                }
                dSquared[i] = minDistance * minDistance;
                sumDSquared += dSquared[i];
            }

            // Choose next center with probability proportional to D(x)^2
            double targetVal = random.nextDouble() * sumDSquared;
            double runningSum = 0;
            int selectedIndex = 0;
            for (int i = 0; i < dSquared.length; i++) {
                runningSum += dSquared[i];
                if (runningSum >= targetVal) {
                    selectedIndex = i;
                    break;
                }
            }

            CrimeDataGenerator.CrimeEvent nextCenter = crimes.get(selectedIndex);
            clusters.add(new Cluster(nextCenter.lat, nextCenter.lon));
        }

        // --- Iterative K-Means Update ---
        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < maxIterations) {
            changed = false;
            iterations++;

            // Clear previous members
            for (Cluster c : clusters) {
                c.members.clear();
            }

            // Assign step
            for (CrimeDataGenerator.CrimeEvent crime : crimes) {
                Cluster nearest = null;
                double minDist = Double.MAX_VALUE;

                for (Cluster c : clusters) {
                    double dist = Graph.haversine(crime.lat, crime.lon, c.lat, c.lon);
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = c;
                    }
                }
                if (nearest != null) {
                    nearest.members.add(crime);
                }
            }

            // Update step
            for (Cluster c : clusters) {
                double oldLat = c.lat;
                double oldLon = c.lon;

                c.recalculateCenter();

                // If center moved more than ~1 meter, keep iterating
                if (Graph.haversine(oldLat, oldLon, c.lat, c.lon) > 1.0) {
                    changed = true;
                }
            }
        }

        // Calculate radius and safety intensity details
        for (Cluster c : clusters) {
            c.calculateRadiusAndIntensity();
        }

        return clusters;
    }
}
