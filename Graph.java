package safepath;

import java.util.*;

public class Graph {
    public static class Node {
        public String id;
        public double lat;
        public double lon;

        public Node(String id, double lat, double lon) {
            this.id = id;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static class Edge {
        public String from;
        public String to;
        public double distance; // in meters
        public double safetyMultiplier; // 1.0 is safe, higher is less safe

        public Edge(String from, String to, double distance, double safetyMultiplier) {
            this.from = from;
            this.to = to;
            this.distance = distance;
            this.safetyMultiplier = safetyMultiplier;
        }

        public double getSafestCost() {
            return distance * safetyMultiplier;
        }
    }

    public Map<String, Node> nodes = new HashMap<>();
    public Map<String, List<Edge>> adjacencyList = new HashMap<>();

    public void addNode(Node node) {
        nodes.put(node.id, node);
        adjacencyList.putIfAbsent(node.id, new ArrayList<>());
    }

    public void addEdge(String from, String to, double distance, double safetyMultiplier) {
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) return;
        Edge edge1 = new Edge(from, to, distance, safetyMultiplier);
        Edge edge2 = new Edge(to, from, distance, safetyMultiplier);
        adjacencyList.get(from).add(edge1);
        adjacencyList.get(to).add(edge2);
    }

    // Procedural generation of a grid-like or connected road network
    public static Graph generateProceduralGraph(double centerLat, double centerLon, int gridSize, double spacingMeters) {
        Graph graph = new Graph();
        double latDegreePerMeter = 1.0 / 111320.0;
        double lonDegreePerMeter = 1.0 / (111320.0 * Math.cos(Math.toRadians(centerLat)));

        // Generate grid of nodes
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                String id = "node_" + i + "_" + j;
                double offsetLat = (i - gridSize / 2.0) * spacingMeters * latDegreePerMeter;
                double offsetLon = (j - gridSize / 2.0) * spacingMeters * lonDegreePerMeter;
                
                // Add some small random perturbation to make it look like organic street network
                double noiseLat = (Math.random() - 0.5) * 0.15 * spacingMeters * latDegreePerMeter;
                double noiseLon = (Math.random() - 0.5) * 0.15 * spacingMeters * lonDegreePerMeter;

                graph.addNode(new Node(id, centerLat + offsetLat + noiseLat, centerLon + offsetLon + noiseLon));
            }
        }

        // Connect nodes to form roads
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                String currentId = "node_" + i + "_" + j;
                Node current = graph.nodes.get(currentId);

                // Connect to right neighbor
                if (j < gridSize - 1) {
                    String rightId = "node_" + i + "_" + (j + 1);
                    Node right = graph.nodes.get(rightId);
                    double dist = haversine(current.lat, current.lon, right.lat, right.lon);
                    graph.addEdge(currentId, rightId, dist, 1.0);
                }

                // Connect to bottom neighbor
                if (i < gridSize - 1) {
                    String bottomId = "node_" + (i + 1) + "_" + j;
                    Node bottom = graph.nodes.get(bottomId);
                    double dist = haversine(current.lat, current.lon, bottom.lat, bottom.lon);
                    graph.addEdge(currentId, bottomId, dist, 1.0);
                }

                // Add some diagonal secondary roads (organic shortcuts) with a probability
                if (i < gridSize - 1 && j < gridSize - 1 && Math.random() < 0.25) {
                    String diagId = "node_" + (i + 1) + "_" + (j + 1);
                    Node diag = graph.nodes.get(diagId);
                    double dist = haversine(current.lat, current.lon, diag.lat, diag.lon);
                    graph.addEdge(currentId, diagId, dist, 1.0);
                }
            }
        }

        return graph;
    }

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public Node findNearestNode(double lat, double lon) {
        Node nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (Node node : nodes.values()) {
            double dist = haversine(lat, lon, node.lat, node.lon);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = node;
            }
        }
        return nearest;
    }
}
