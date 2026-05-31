package safepath;

import java.util.*;

public class AStarAlgorithm {
    public static class PathResult {
        public List<Graph.Node> path;
        public double totalDistance; // real distance in meters
        public double averageSafetyScore; // average safety score along the path

        public PathResult(List<Graph.Node> path, double totalDistance, double averageSafetyScore) {
            this.path = path;
            this.totalDistance = totalDistance;
            this.averageSafetyScore = averageSafetyScore;
        }
    }

    private static class PQNode implements Comparable<PQNode> {
        String id;
        double gCost; // accumulated safety-weighted cost
        double fCost; // gCost + hCost (heuristic)

        PQNode(String id, double gCost, double fCost) {
            this.id = id;
            this.gCost = gCost;
            this.fCost = fCost;
        }

        @Override
        public int compareTo(PQNode other) {
            return Double.compare(this.fCost, other.fCost);
        }
    }

    public static PathResult computeSafestPath(Graph graph, String startId, String endId, SafetyScorer safetyScorer, String timeOfDay) {
        if (!graph.nodes.containsKey(startId) || !graph.nodes.containsKey(endId)) {
            return new PathResult(new ArrayList<>(), 0, 100.0);
        }

        Graph.Node targetNode = graph.nodes.get(endId);

        Map<String, Double> gCosts = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        PriorityQueue<PQNode> pq = new PriorityQueue<>();
        Set<String> visited = new HashSet<>();

        for (String nodeId : graph.nodes.keySet()) {
            gCosts.put(nodeId, Double.MAX_VALUE);
        }

        gCosts.put(startId, 0.0);
        double initialH = Graph.haversine(graph.nodes.get(startId).lat, graph.nodes.get(startId).lon, targetNode.lat, targetNode.lon);
        pq.add(new PQNode(startId, 0.0, initialH));

        while (!pq.isEmpty()) {
            PQNode current = pq.poll();
            String u = current.id;

            if (u.equals(endId)) break;
            if (visited.contains(u)) continue;
            visited.add(u);

            List<Graph.Edge> neighbors = graph.adjacencyList.get(u);
            if (neighbors == null) continue;

            for (Graph.Edge edge : neighbors) {
                String v = edge.to;
                if (visited.contains(v)) continue;

                // Dynamically calculate safety multiplier for edge
                Graph.Node fromNode = graph.nodes.get(u);
                Graph.Node toNode = graph.nodes.get(v);
                double edgeMultiplier = safetyScorer.calculateEdgeSafetyMultiplier(fromNode, toNode, timeOfDay);

                double newGCost = gCosts.get(u) + (edge.distance * edgeMultiplier);
                if (newGCost < gCosts.get(v)) {
                    gCosts.put(v, newGCost);
                    parentMap.put(v, u);
                    
                    double h = Graph.haversine(toNode.lat, toNode.lon, targetNode.lat, targetNode.lon);
                    pq.add(new PQNode(v, newGCost, newGCost + h));
                }
            }
        }

        // Reconstruct path
        List<Graph.Node> path = new ArrayList<>();
        String curr = endId;
        while (curr != null) {
            path.add(0, graph.nodes.get(curr));
            curr = parentMap.get(curr);
        }

        if (path.isEmpty() || !path.get(0).id.equals(startId)) {
            return new PathResult(new ArrayList<>(), 0, 100.0);
        }

        // Calculate actual physical distance and average safety score
        double totalDistance = 0.0;
        double safetyScoreSum = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            Graph.Node from = path.get(i);
            Graph.Node to = path.get(i + 1);
            totalDistance += Graph.haversine(from.lat, from.lon, to.lat, to.lon);
            
            double midLat = (from.lat + to.lat) / 2.0;
            double midLon = (from.lon + to.lon) / 2.0;
            safetyScoreSum += safetyScorer.computeSafetyScore(midLat, midLon, timeOfDay);
        }

        double averageSafety = path.size() > 1 ? (safetyScoreSum / (path.size() - 1)) : 100.0;

        return new PathResult(path, totalDistance, averageSafety);
    }
}
