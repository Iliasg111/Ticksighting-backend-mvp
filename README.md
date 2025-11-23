Video : https://youtu.be/AVJ_4ycXv5A


 TickMVP – Tick Sighting Backend (Java)

A lightweight, dependency-free Java backend that ingests tick sighting data from CSV, exposes analytics endpoints, generates hotspot maps, and includes a simple prediction model.

This project is designed to be **easy to run**, **framework-free**, and **fully compatible with Eclipse**.

---





� Project Structure

tick-simple-crossplatform/
tick-plain-http/
src/
TickServer.java
data/
tick_sightings.csv
bin/
README.txt

The actual Java project root is:

tick-simple-crossplatform/tick-plain-http

This is the folder you MUST import into Eclipse.

---

How to Run the Project in Eclipse 

### 1. Download the repository  
Click **Code → Download ZIP** and unzip the folder.

### 2. Open Eclipse  
Use any Java workspace.

### 3. Import the project  
This is important — select the correct folder:

1. **File → Import**
2. **General → Existing Projects into Workspace**
3. **Select root directory → Browse**
4. Choose:

.../Ticksighting-backend-mvp-main/tick-simple-crossplatform/tick-plain-http

5. Eclipse will detect the project.  
6. Click **Finish**.

### 4. Run the server
1. Open `TickServer.java`
2. Right-click → **Run As → Java Application**
3. Console will show:

if that does not work or it says 'no projects found' do this:
1. Start Eclipse.
2. File -> open projects from file system
3. select the extracted ZIP contents
4. select tick-plain-HTTP
Open src/TickServer.java
Run it using Run As → Java Application




Server started on port 8000

### 5. Test the endpoints  
Open any browser:

- http://localhost:8000/sightings  
- http://localhost:8000/species  
- http://localhost:8000/hotspots  
- http://localhost:8000/predict  

---

 API Endpoints

### **GET /sightings**
Returns raw tick sighting dataset.

### **GET /species**
Returns species frequency analytics.

### **GET /hotspots**
Aggregates sightings by region to identify hotspots.

### **GET /predict**
Outputs a simple trend-based prediction of future tick activity.

---

 Architecture Decisions

### ✔ Plain Java  
No frameworks, no Maven, no external dependencies.  
This keeps the project:
- lightweight  
- portable  
- easy to run in Eclipse  

### ✔ Simple HTTP Server  
A custom server handles requests without Spring or Tomcat overhead.

### ✔ In-memory CSV processing  
The CSV dataset is loaded at startup into memory using simple Java parsing.

### ✔ Modular endpoint logic  
Each route has its own processing block, making the code easy to modify or extend.

### ✔ Improved error handling  
Catches:
- invalid CSV rows  
- missing URL parameters  
- malformed requests  
- failed lookups  

Prevents crashes and returns useful error messages.

---

#  How the System Consumes & Presents Data

### **1. Data Loading**  
On startup, the server reads:

data/tick_sightings.csv

Each row becomes a Java object representing:
- species  
- date  
- location  
- additional fields  

### **2. Analytics Layer**  
Depending on the endpoint, the system performs:
- grouping  
- counting  
- aggregation  
- simple trend prediction  

### **3. Output Format**  
All results are returned as JSON-formatted text.

Example `/species` output:

{
"Ixodes ricinus": 145,
"Dermacentor reticulatus": 62
}

---

# Future Improvements (If More Time Provided)

### 1️⃣ Convert to Maven/Gradle project  
Better build structure and dependency management.

### 2️⃣ Replace custom server with Spring Boot  
Cleaner routing, built-in JSON, improved error handling.

### 3️⃣ Move from CSV to a real database  
Use PostgreSQL or MongoDB for scalability.

### 4️⃣ More advanced prediction model  
Use time-series or machine learning for better forecasts.

### 5️⃣ Add a front-end dashboard  
Interactive heatmaps, maps, graphs, and live predictions.

### 6️⃣ Add automated tests  
Unit + integration tests for robustness.







