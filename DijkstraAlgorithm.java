package safepath;

import java.util.*;

public class DijkstraAlgorithm {
    public static class PathResult {
        public List<Graph.Node> path;
        public double totalDistance; // in meters

        public PathResult(List<Graph.Node> path, double totalDistance) {
            this.path = path;
            this.totalDistance = totalDistance;
        }
    }

    private static class PQNode implements Comparable<PQNode> {
        String id;
        double distance;

        PQNode(String id, double distance) {
            this.id = id;
            this.distance = distance;
        }

        @Override
        public int compareTo(PQNode other) {
            return Double.compare(this.distance, other.distance);
        }
    }

    public static PathResult computeShortestPath(Graph graph, String startId, String endId) {
        if (!graph.nodes.containsKey(startId) || !graph.nodes.containsKey(endId)) {
            return new PathResult(new ArrayList<>(), 0);
        }

        Map<String, Double> distances = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        PriorityQueue<PQNode> pq = new PriorityQueue<>();
        Set<String> visited = new HashSet<>();

        for (String nodeId : graph.nodes.keySet()) {
            distances.put(nodeId, Double.MAX_VALUE);
        }

        distances.put(startId, 0.0);
        pq.add(new PQNode(startId, 0.0));

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

                double newDist = distances.get(u) + edge.distance;
                if (newDist < distances.get(v)) {
                    distances.put(v, newDist);
                    parentMap.put(v, u);
                    pq.add(new PQNode(v, newDist));
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
            return new PathResult(new ArrayList<>(), 0);
        }

        return new PathResult(path, distances.get(endId));
    }
}
