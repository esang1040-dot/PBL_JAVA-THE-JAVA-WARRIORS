# SafePath — Smart Women's Safety Navigation System

SafePath is a high-performance, smart navigation desktop/web platform designed to enhance women's safety by integrating real-time safety analytics into route planning.

The backend is built in pure **Java SE** (no external library overhead, using `com.sun.net.httpserver` and Java Standard Library), while the frontend is constructed as a modern, high-fidelity **Single Page Application (SPA)** utilizing **Vanilla HTML5, CSS3, and ES6+ JavaScript** featuring a stunning **dark glassmorphism** aesthetic.

---

## 🚀 Key Features

1. **A* Safest Routing Algorithm**: Computes the safest path by scaling physical haversine distances with safety multipliers derived from spatial crime clusters.
2. **Dijkstra Shortest Path**: Finds the fastest/shortest physical route to compare against the safer alternative.
3. **K-Means++ Crime Hotspot Clustering**: Clusters historical simulated crime occurrences into high-risk safety centroids with calculated hazard zones and radii.
4. **Dynamic Dark Glassmorphism UI**: High-fidelity dark mode panel overlays designed specifically for visibility and comfort in low-light night navigation.
5. **Real-time Local Safety Score Dial**: Gauges the current live safety rating of any selected point on the map.
6. **UV Index Alerts Service**: Proxies and color-codes UV forecasts from the Open-Meteo API, providing dynamic safety tips.
7. **Emergency SOS Integration**: Interactive emergency alarm bells, GPS transmitter, and proximity searches using OpenStreetMap's Overpass API to list nearby police stations, medical centers, and support lines.

---

## 🛠️ Architecture Design

- **Java Backend Server** (`SafePathServer.java`): A lightweight multi-threaded HTTP server hosted on port `8080` that manages static files and provides clean JSON REST API endpoints.
- **Dijkstra & A* Implementations**: Written purely from scratch to handle standard weighted graphs and custom heuristic evaluations.
- **K-Means++ Core Clustering Engine**: Groups crime points using probability-based center selection ($D(x)^2$) for optimal and fast convergence.
- **External Services**: Queries live APIs for weather (Open-Meteo) and geo-spatial features (Overpass API for OpenStreetMap nodes), with robust, offline simulated fallbacks to ensure full sandbox/offline resilience.

---

## 📂 Project Structure

```
safepath/
├── src/
│   └── safepath/
│       ├── SafePathServer.java       # HTTP Server & API Handlers
│       ├── Graph.java                # Procedural road network generator
│       ├── DijkstraAlgorithm.java    # Fastest Route finder
│       ├── AStarAlgorithm.java       # Safest Route finder
│       ├── KMeansClustering.java     # K-Means++ hotspot cluster engine
│       ├── SafetyScorer.java         # Safety scoring calculations
│       ├── CrimeDataGenerator.java   # Simulated historical incident dataset
│       ├── UVIndexService.java       # Open-Meteo API proxy & recommendations
│       └── EmergencyService.java     # Overpass API parser & police/hospitals locator
├── public/
│   ├── index.html                    # Single Page App Layout
│   ├── css/
│   │   └── style.css                 # Dark Glassmorphism CSS Stylesheet
│   └── js/
│       ├── app.js                    # Core app coordinator
│       ├── map.js                    # Leaflet dark map renderer & markers
│       ├── routing.js                # API pathfinding handler & comparison
│       ├── safety.js                 # Safety score dial & hotspots controller
│       ├── emergency.js              # SOS alarm & emergency facility list
│       └── uv.js                     # UV index alert display logic
├── compile.sh                        # Build & execution automation script
└── README.md                         # Project documentation
```

---

## 🏁 How to Run

### Prerequisite
Make sure you have **Java Development Kit (JDK) 11 or higher** installed.

### Step 1: Build the Project
Run the automated compile script:
```bash
./compile.sh
```

### Step 2: Start the SafePath Server
Run the compiled Java class:
```bash
java -cp bin safepath.SafePathServer
```
*(Alternatively, you can just run `./compile.sh run` to build and start in one go!)*

### Step 3: Open the App
Open your web browser and navigate to:
**[http://localhost:8080](http://localhost:8080)**

---

## 🗺️ How to Test & Use the App

1. **Map Interaction**: 
   - Click anywhere on the map to set your **Starting Point** (indicated by a blue marker). The app immediately updates the safety score dial, local UV warnings, and triggers emergency queries to fetch police/medical centers within 2km.
   - Click another point to set your **Destination Point** (indicated by a green marker).
   
2. **Route Comparison**:
   - Once both points are set, SafePath auto-calculates. The map renders the **Safest path in glowing green** and the **Shortest path in translucent red** with dash marks.
   - A sidebar card pops up showing a comparison: the physical distance, walk duration, and safety ratings of each.
   - Toggle between cards to highlight either route on the active map display.

3. **Time-of-Day Dynamics**:
   - Change the dropdown in the header to **Night**, **Evening**, **Afternoon**, or **Morning**. The safety scorer dynamically scales the hotspot multipliers (risk increases at night!), causing A* to calculate completely different routes depending on the time!

4. **SOS Alert**:
   - Tap the pulsating red **SOS TRIGGER** button in the header. An alarm siren sounds, a glowing modal overlay opens with live GPS coordinates, and police/helpline connections are initialized. Click cancel to dismiss.
