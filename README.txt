Overview of classes:

GUIController
	-Handles interaction with GUI
	-Initializes all other classes
	
SerialCommunicator
	-Responsible for direct interaction with Xbee (serial interfaces)
	-Responsible for launching and interacting with async reader
	
dataManager
	-Responsible for displaying and dealing with data received
	

Libraries used:
-https://github.com/stleary/JSON-java
	-For JSON parsing for the maps
-https://github.com/Fazecast/jSerialComm
	-For serial communication 