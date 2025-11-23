TickSightings Backend (MVP)

A lightweight Java HTTP server for recording and analysing tick sightings.
Built with plain Java, no external dependencies, and designed for simplicity.

 Features

Serve tick sighting data from a lightweight HTTP server

Filter sightings by date and location

Species analytics (optional extension)

Hotspot identification (optional extension)

Prediction-ready structure for future models

 Project Structure
tick-simple-crossplatform/
 ├── tick-plain-http/
 │    ├── src/           # Java source files
 │    ├── data/          # JSON/CSV local datasets
 │    ├── bin/           # Compiled classes
 │    └── README.txt

 Running the Server (Eclipse)

Open Eclipse

File → Import → Existing Projects into Workspace

Select the project root folder

Finish the import

Open src/TickServer.java

Run it using Run As → Java Application

if that does not work or it says 'no projects found' do this:

1. Start Eclipse.
2. File -> open projects from file system
3. select the extracted ZIP contents
4. select tick-plain-HTTP

Open src/TickServer.java

Run it using Run As → Java Application


Server starts at:

http://localhost:8080

Endpoints
1. List Sighting Records
GET /sightings?from=YYYY-MM-DD&to=YYYY-MM-DD&location=London

2. Species Analytics (extension)
GET /species/analytics

3. Hotspot Map (extension)
GET /hotspots

4. Simple Prediction (extension)
GET /predict?location=London

 Data Files

Stored in /data

Server reads directly from JSON/CSV assets

No external database required

🛠 Requirements

Java 8+

Eclipse or any IDE supporting running plain Java applications
