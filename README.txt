Overview of classes:

GUIController
	-Handles interaction with GUI
	-Draws map
	-Plots aircraft movement on map
	-Initializes all other classes
	
SerialCommunicator
	-Responsible for initiating serial connection with Xbee
	-Responsible for launching and interacting with async reader (readPackets)
	-Responsible for ensuing well-being of readPackets
	
dataManager
	-Responsible for updating data (stats) displayed on screen
	-Responsible for parsing incoming data
	-Responsible for tracking distance traveled, and from takeoff
	
readPackets
	-Responsible for checking for available packets
	-Responsible for identifying incoming bytes
	-Responsible for packaging and passing on bytes (to dataManager)

TextAreaHandler
	-Responsible for managing logger output to textArea (on GUI)
	
KMLStore
	-Utility class
	-Takes name, latitude, and logitude value, then exports list to .kml
	
tuple
	-Utility class
	-Format for lat/long coordinates
	-Makes code cleaner... doesn't really do anything...
	
	

Libraries used:
-https://github.com/stleary/JSON-java
	-For JSON parsing for the maps
-https://github.com/Fazecast/jSerialComm
	-For serial communication 