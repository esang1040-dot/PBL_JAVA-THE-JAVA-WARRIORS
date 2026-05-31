package safepath;

import java.util.*;

public class SafetyScorer {
    private List<KMeansClustering.Cluster> hotspots;

    public SafetyScorer(List<KMeansClustering.Cluster> hotspots) {
        this.hotspots = hotspots;
    }

    /**
     * Compute a safety score from 0 (extremely dangerous) to 100 (fully safe)
     * at a given coordinate, optionally considering the time of day.
     */
    public double computeSafetyScore(double lat, double lon, String timeOfDay) {
        double score = 100.0;
        
        // Scale factor based on time of day
        double timeMultiplier = 1.0;
        if (timeOfDay != null) {
            switch (timeOfDay.toLowerCase()) {
                case "evening":
                    timeMultiplier = 1.25;
                    break;
                case "night":
                    timeMultiplier = 1.6;
                    break;
                case "morning":
                    timeMultiplier = 0.8;
                    break;
                default:
                    timeMultiplier = 1.0;
            }
        }

        for (KMeansClustering.Cluster hotspot : hotspots) {
            double dist = Graph.haversine(lat, lon, hotspot.lat, hotspot.lon);
            
            // Gaussian penalty decay based on distance and hotspot radius
            double ratio = dist / hotspot.radiusMeters;
            double penalty = hotspot.intensity * 0.1 * Math.exp(-0.5 * ratio * ratio) * timeMultiplier;
            
            score -= penalty;
        }

        return Math.max(10.0, Math.min(100.0, score)); // Min score of 10 for any point
    }

    /**
     * Calculates the safety multiplier for an edge. A higher multiplier means
     * A* will find the path more expensive, and therefore steer away from it.
     */
    public double calculateEdgeSafetyMultiplier(Graph.Node from, Graph.Node to, String timeOfDay) {
        // Average coordinates of the edge
        double midLat = (from.lat + to.lat) / 2.0;
        double midLon = (from.lon + to.lon) / 2.0;

        double safetyScore = computeSafetyScore(midLat, midLon, timeOfDay);

        // Map safety score [10, 100] to a multiplier [1.0, 15.0]
        // 100 safety score -> 1.0 multiplier (no extra cost)
        // 10 safety score -> 15.0 multiplier (highly expensive)
        double norm = (100.0 - safetyScore) / 90.0; // 0 to 1
        return 1.0 + (norm * 14.0); 
    }
}
