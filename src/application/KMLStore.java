package application;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class KMLStore {
	private static final Logger log = Logger.getLogger(KMLStore.class.getName());
	class Placemark {
		private String name;
		private float longitude;
		private float latitude;
		public Placemark(String id, float lng, float lat) {
			name = id;
			longitude =  lng;
			latitude = lat;
		}
		public String getName() {
			return name;
		}
		public float getLat() {
			return latitude;
		}
		public float getLng() {
			return longitude;
		}
	}
	private ArrayList<Placemark> coords;
	public KMLStore() {
		log.addHandler(GUIController.taHandle);
		log.addHandler(GUIController.filehandle);
		coords = new ArrayList<Placemark>();
		log.log(Level.FINER, "KML storing object initialized.");
	}
	public void addCord(String id, float lng, float lat) {
		coords.add(new Placemark(id, lng, lat));
		log.log(Level.FINEST, "Adding new coordinate");
	}
	public void exportKML() {
		log.log(Level.INFO, "KML export requested.");
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			//Start document builders
			docBuilder = docFactory.newDocumentBuilder();
			Document KMLOut = docBuilder.newDocument();
			//Base of document
			Element preRoot = KMLOut.createElement("kml");
			Attr KMLTp = KMLOut.createAttribute("xmlns");
			KMLTp.setValue("http://www.opengis.net/kml/2.2");
			KMLOut.appendChild(preRoot);
			preRoot.setAttributeNode(KMLTp);
			//Create folder for points
			Element root = KMLOut.createElement("Folder");
			preRoot.appendChild(root);
			log.log(Level.FINER, "Document created.");

			/*
			 * KML -> preRoot
			 * 	Folder -> root
			 * 	 Placemark -> newBit
			 * 	  name -> id
			 * 	  Point -> pt
			 * 	   coordinates -> coordinates
			 */
			for(Placemark cord : coords)  {
				//Create Placemark
				Element newBit = KMLOut.createElement("Placemark");
				root.appendChild(newBit);
				//Add name
				Element id = KMLOut.createElement("name");
				id.appendChild(KMLOut.createTextNode(cord.getName()));
				newBit.appendChild(id);
				//Add point
				Element pt = KMLOut.createElement("Point");
				newBit.appendChild(pt);
				//Add coordinates
				Element coordinates = KMLOut.createElement("coordinates");
				String cordStr = Float.toString(cord.getLat()) + "," + Float.toString(cord.getLng()) + ",0";
				coordinates.appendChild(KMLOut.createTextNode(cordStr));
				pt.appendChild(coordinates);
				log.log(Level.FINEST, "Coordinate added.");
			}

			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer trans = factory.newTransformer();
			DOMSource source = new DOMSource(KMLOut);
			StreamResult output = new StreamResult(new File("KMLOut.kml"));
			trans.transform(source, output);
			log.log(Level.INFO, "Document exported successfully.");

		} catch (ParserConfigurationException e) {
			log.log(Level.SEVERE, "Parser error.");
			log.log(Level.SEVERE, e.toString());
		} catch (TransformerConfigurationException e)  {
			log.log(Level.SEVERE, "Transformer error.");
			log.log(Level.SEVERE, e.toString());
		} catch (TransformerException e) {
			log.log(Level.SEVERE, "Transformer exception.");
			log.log(Level.SEVERE, e.toString());
		}
	}
	/*
	public static void main(String args[]) {
		KMLStore sv = new KMLStore();
		sv.addCord("One", (float)44.224455, (float)-76.498260);
		sv.addCord("Two", (float)44.229315, (float)-76.486250);
		//sv.addCord("Three", 44.229315, -76.486250);
		sv.exportKML();
	} */
}
