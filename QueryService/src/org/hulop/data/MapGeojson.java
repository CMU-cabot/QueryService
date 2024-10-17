package org.hulop.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.wink.json4j.JSON;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.hulop.data.i18n.Messages;

public class MapGeojson {
	private static String KEY_PROP_BUILDING = "hulop_building";
	private static String KEY_NODE = "node";
	private static String KEY_PROP_CATEGORY = "facil_type";
	private static String KEY_NODE_HEIGHT = "hulop_height";
	private static String KEY_NAME = "name";
	private static String KEY_NAME_PRON = "name_pron";
	private static String KEY_EXIT = "exit";
	private static String KEY_EXIT_PRON = "exit_pron";
	/*
	 * 1: public offices, etc.
	 * 2: educational and cultural facilities, etc.
	 * 3: medical facilities
	 * 4: health and welfare facilities
	 * 5: commercial facilities
	 * 6: accommodations
	 * 7: parks and athletic facilities
	 * 8: tourist facilities
	 * 9: transport facilities
	 * 10: public toilets (standalone)
	 * 99: other facilities
	 */
	private static String CATEGORY_TOILET = "10";
	private static String CATEGORY_FACILITY = "公共施設の情報";  // TODO
	/*
	 * 1: none
	 * 2: general toilets
	 * 3: multi-functional toilets (without equipment for ostomates, nor diaper change bed)
	 * 4: multi-functional toilets (with equipment for ostomates)
	 * 5: multi-functional toilets (with diaper change bed)
	 * 6: multi-functional toilets (with equipment for ostomates, and diaper change bed)
	 * 99: unknown"
	 */
	private static String KEY_PROP_TOILET = "toilet";
	private static String KEY_SEX = "sex";
	private static String SEX_MALE = "1";
	private static String SEX_FEMALE = "2";
	private static String SEX_SHARED = "3";  // TODO
	private static String KEY_MAJOR_CATEGORY = "hulop_major_category";
	private static String KEY_SUB_CATEGORY = "hulop_sub_category";
	private static String KEY_PROP_FACILITY_ID = "facility_id";
	
	public class Facility {
		private JSONObject feature;
		private String name;
		private String pron;
		private String building;
		private String floor;
		private String nodeID;
		private String majorCategory;

		public String toString() {
			try {
				return String.format("%s@%s (%s)\n%s", name, building, nodeID, feature.toString(2));
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return "";
		}
		public Facility(JSONObject feature, String name, String pron, String building, String floor, String nodeID, String majorCategory) {
			this.feature = feature;
			assert(name == null);
			this.name = name;
			this.pron = pron;
			this.building = building;
			this.floor = floor;
			this.nodeID = nodeID;
			this.majorCategory = majorCategory;
		}
		public boolean combine(JSONObject feature,  String name, String pron, String building, String floor, String nodeID, String majorCategory) {
			if (!this.building.equals(building)) {
				return false;
			}
			this.nodeID = this.nodeID+"|"+nodeID;
			if (!this.floor.equals(floor)) {
				this.floor = "";
			}
			return true;
		}
		public String getName() {
			return name;
		}
		public String getNamePron() {
			return pron;
		}
		public String getBuilding() {
			return building;
		}
		public String getFloor() {
			return floor;
		}
		public String getNodeID() {
			return nodeID;
		}
		public boolean isService() {
			return false;
		}
		public String getMajorCategory() {
			return building;
		}
	}
	public class ServiceFacility extends Facility {		
		public ServiceFacility(JSONObject feature, String name, String pron, String building, String floor, String nodeID, String majorCategory) {
			super(feature, name, pron, building, floor, nodeID, majorCategory);
		}
		public boolean isService() {
			return true;
		}
	}
	public class GroupFacility extends Facility {
		List<Facility> facilities;
		public GroupFacility(List<Facility> facilities) {
			super(null, facilities.get(0).getName(), facilities.get(0).getNamePron(), facilities.get(0).getBuilding(), facilities.get(0).getFloor(), null, null);
			this.facilities = facilities;
		}
		public String getNodeID() {
			String id = "";
			boolean first = true;
			for(Facility f:facilities) {
				id += (first?"":"|")+f.getNodeID(); 
				first = false;
			}
			return id;
		}
	}
	public class Toilet extends ServiceFacility {
		String sex;
		String type;
		public Toilet(JSONObject feature, String building, String nodeID, String floor, String sex, String type, String majorCategory) {
			super(feature, null, null, building, nodeID, floor, majorCategory);
			this.sex = sex;
			this.type = type;
		}
		public String getName() {
			if (SEX_MALE.equals(sex)) {
				return Messages.get(MapGeojson.this.locale, "male_restroom");
			}
			if (SEX_FEMALE.equals(sex)) {
				return Messages.get(MapGeojson.this.locale, "female_restroom");
			}
			return Messages.get(MapGeojson.this.locale, "restroom");
		}
		public String getNamePron() {
			return getName();
		}
	}
	
	public ArrayList<Facility> facilities = new ArrayList<Facility>();	

	public static MapGeojson load(URL url, Locale locale) {
		try {
			return new MapGeojson(url.openStream(), locale);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private Locale locale;
	protected JSONObject json;
	public MapGeojson(InputStream is, Locale locale) {
		this.locale = locale;
		try {
			json = (JSONObject) JSON.parse(is);
			analyze(json);
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	private void analyze(JSONObject data) throws JSONException {
		if (!data.has("landmarks")) {
			return;
		}
		JSONArray features = data.getJSONArray("landmarks");
		
		for(int i = 0; i < features.length(); i++) {
			Facility facility = createFacility(features.getJSONObject(i));
			if (facility != null) {
				facilities.add(facility);
			}
		}
	}
	private String get(JSONObject properties, String key) throws JSONException {
		String value = null;
		if (properties.has(key)) {
			value = properties.getString(key);
		}
		return value;
	}

	HashMap<String, Facility> map = new HashMap<String, Facility>();
	String[] services = {"NURS", "SMOK", "ATM", "RE_L", "_facility_"};
	public Facility createFacility(JSONObject feature) throws JSONException {
		JSONObject properties = feature.getJSONObject("properties");
		
		String nodeID = get(feature, KEY_NODE);
		String category = get(properties, KEY_PROP_CATEGORY);
		String facilityID = get(properties, KEY_PROP_FACILITY_ID);		
		
		if (nodeID == null) {
			return null;
		}
					
		String name = get(feature, KEY_NAME);
		String namePron = get(feature, KEY_NAME_PRON);
		String exit = get(feature, KEY_EXIT);
		String exitPron = get(feature, KEY_EXIT_PRON);
		
		String building = get(properties, KEY_PROP_BUILDING);
		String floor = get(feature, KEY_NODE_HEIGHT);
		String majorCategory = get(properties, KEY_MAJOR_CATEGORY);
		String subCategory = get(properties, KEY_SUB_CATEGORY);
		
		Facility facility = map.get(facilityID);
		if (facility != null) {
			if (facility.combine(feature, name, namePron, building, floor, nodeID, majorCategory)) {
				return null;
			}
			facility = null; // if it is not combined, create new one
		}

		if (building == null) {
			building = "Others";
		}
		if ((name != null && name.length() > 0) || (exit != null && exit.length() > 0)) {
			if (subCategory != null && Arrays.asList(services).contains(subCategory)) {
				facility = new ServiceFacility(feature, exit+name, exitPron+namePron, building, floor, nodeID, majorCategory);
			} else {
				facility = new Facility(feature, exit+name, exitPron+namePron, building, floor, nodeID, majorCategory);
			}
		} else if (CATEGORY_TOILET.equals(category)) {
			String sex = get(properties, KEY_SEX);
			String toilet = get(properties, KEY_PROP_TOILET);
			facility = new Toilet(feature, building, floor, nodeID, sex, toilet, majorCategory);
		} else if (CATEGORY_FACILITY.equals(category)) {					
			//System.err.println("no name facility: "+nodeID);					
		} else {
			for(Object key:properties.keySet()) {
				System.err.println(key+": "+properties.getString((String) key));
			}
			System.err.println("----");
		}
		
		map.put(facilityID, facility);
		
		return facility;
	}
	
	public String[] getBuildings() {
		HashSet<String> buildings = new HashSet<String>();
		for(Facility f: facilities){
			if (!validName(f.building)) continue;
			buildings.add(f.building);
		}
		return buildings.toArray(new String[0]);
	}

	private boolean validName(String building) {
		return building != null && !building.startsWith("_");
	}

	public String[] getMajorCategories() {
		HashSet<String> categories = new HashSet<String>();
		for(Facility f: facilities){
			if (!validName(f.majorCategory)) continue;
			categories.add(f.majorCategory);
		}
		return categories.toArray(new String[0]);
	}

	public List<Facility> getFacilitiesByBuilding(String building) {
		return facilities.stream().filter(f -> validName(f.building) && f.building.equals(building))
				.collect(Collectors.<Facility>toList());
	}
	
	public List<Facility> getFacilitiesByMajorCategory(String category) {
		return facilities.stream().filter(f -> validName(f.majorCategory) && f.majorCategory.equals(category))
				.collect(Collectors.<Facility>toList());
	}
	
	public List<Facility> getServices() {
		List<Facility> list = facilities.stream().filter(f -> f.isService())
				.collect(Collectors.<Facility>toList());
		
		HashMap<String, ArrayList<Facility>> map = new HashMap<String, ArrayList<Facility>>();
		for(Facility f:list) {
			ArrayList<Facility> list2 = map.get(f.getName());
			if (list2 == null) {
				list2 = new ArrayList<Facility>();
				map.put(f.getName(), list2);
			}
			list2.add(f);
		}
		
		ArrayList<Facility> ret = new ArrayList<Facility>();
		for(String key:map.keySet()) {
			ret.add(new GroupFacility(map.get(key)));
		}
		return ret;		
	}
	
	public interface FacilityEntry {
		public boolean matches(Facility facility);
	}
	public Facility find(FacilityEntry entry) {
		for(Facility f:facilities) {
			if (entry.matches(f)) {
				return f;
			}
		}
		return null;
	}

	public JSONArray getLandmarks() throws JSONException {
		if (json.has("landmarks")) {
			return json.getJSONArray("landmarks");
		}
		return null;
	}

}
