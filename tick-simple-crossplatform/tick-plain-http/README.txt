Tick Sightings Lightweight HTTP Server
=========================================

This is a plain Java project with NO external dependencies.

How to open in Eclipse
----------------------

1. Start Eclipse.
2. File -> Import...
3. Select "Existing Projects into Workspace" (under General).
4. Click "Next".
5. In "Select root directory", browse to the unzipped folder of this project.
6. The project should appear; tick it and click "Finish".

How to run
----------

1. In the Package/Project Explorer, expand the project and open `src/TickServer.java`.
2. Right-click on `TickServer.java`.
3. Choose "Run As" -> "Java Application".

The server will start on: http://localhost:8080

Available endpoints
-------------------

1) List sightings with optional filter by location:

   GET /sightings?from=YYYY-MM-DD&to=YYYY-MM-DD&location=London

     http://localhost:8080/sightings?from=2019-01-01&to=2020-01-01&location=London

2) Aggregate counts per region (location):

   GET /regions?from=YYYY-MM-DD&to=YYYY-MM-DD

 
     http://localhost:8080/regions?from=2014-01-01&to=2024-01-01

3) Trends over time (monthly by default or weekly):

   GET /trends?from=YYYY-MM-DD&to=YYYY-MM-DD&granularity=monthly
   GET /trends?from=YYYY-MM-DD&to=YYYY-MM-DD&granularity=weekly


     http://localhost:8080/trends?from=2014-01-01&to=2024-01-01&granularity=monthly
     http://localhost:8080/trends?from=2014-01-01&to=2024-01-01&granularity=weekly

Dataset
-------

The tick sightings data (1000 rows) is stored in:

  data/tick_sightings.csv

It was converted from the provided Excel file.


2. Region Aggregation (number of sightings per region)
GET /regions?from=YYYY-MM-DD&to=YYYY-MM-DD




http://localhost:8080/regions?from=2014-01-01&to=2025-01-01


Returns:

[
  { "region": "London", "count": 132 },
  { "region": "Manchester", "count": 88 }
]

 3. Trends (weekly or monthly)
GET /trends?from=YYYY-MM-DD&to=YYYY-MM-DD&granularity=monthly


or

...&granularity=weekly


http://localhost:8080/trends?from=2014-01-01&to=2025-01-01&granularity=monthly

 4. Species Analytics (NEW)
GET /species?from=YYYY-MM-DD&to=YYYY-MM-DD&location=London



http://localhost:8080/species?from=2019-01-01&to=2020-01-01


Returns a sorted list of species, Latin names, and total sightings.

 5. Hotspot Intensity Map (NEW)
GET /hotspots?from=YYYY-MM-DD&to=YYYY-MM-DD




http://localhost:8080/hotspots?from=2020-01-01&to=2022-01-01


Response includes:

region

count

intensity score (0.0–1.0)

This can be directly consumed by a heatmap frontend.

 6. Prediction Model (NEW)
GET /forecast?from=YYYY-MM-DD&to=YYYY-MM-DD&monthsAhead=3


:

http://localhost:8080/forecast?from=2014-01-01&to=2024-01-01&monthsAhead=6




/**
 * This is a Standalone HTTP server for tick sightings backed by a CSV file.
 *
 * Features:
 *  - Cross-platform CSV loading (Windows/macOS/Linux)
 *  - Cleans and validates the raw dataset
 *  - Handles missing / invalid data robustly
 *  - Detects duplicate IDs
 *
 * Endpoints:
 *  - GET /sightings?from=YYYY-MM-DD&to=YYYY-MM-DD&location=London
 *  - GET /regions?from=YYYY-MM-DD&to=YYYY-MM-DD
 *  - GET /trends?from=YYYY-MM-DD&to=YYYY-MM-DD&granularity=monthly|weekly
 *  - GET /species?from=YYYY-MM-DD&to=YYYY-MM-DD&location=London
 *  - GET /hotspots?from=YYYY-MM-DD&to=YYYY-MM-DD
 *  - GET /forecast?from=YYYY-MM-DD&to=YYYY-MM-DD&monthsAhead=3
 */



















