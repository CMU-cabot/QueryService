package org.hulop.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.hulop.data.MapGeojson.Facility;
import org.hulop.data.i18n.Messages;

public class Directory implements Searchable, Cloneable {
	
	private ArrayList<Section> sections = new ArrayList<Section>();
	public Boolean showSectionIndex = false;
	private JSONArray landmarks;

	public Directory() {
		// TODO Auto-generated constructor stub
	}
	
	public Directory(URL featuresUrl, URL nodemapUrl, Locale locale, boolean groupBuilding, boolean groupFloor, boolean groupCategory, boolean groupNearbyFacility) {
		MapGeojson features = null;
		MapGeojson nodemap = null;
		try {
			features = MapGeojson.load(featuresUrl, locale);
			nodemap = MapGeojson.load(nodemapUrl, locale);
			setLandmakrks(features.getLandmarks());
		}catch(Exception e) {
			e.printStackTrace();
			return;
		}

		Comparator<Directory.Item> itemComparator = new Comparator<Directory.Item>() {
			@Override
			public int compare(Item o1, Item o2) {
				return o1.compare(o2);
			}
		};
		FirstLetterIndex firstLetterIndex = new FirstLetterIndex();

		if (groupBuilding && features.getBuildings().length > 1) {
			Section buildingsSection = this.add(new Section(Messages.get(locale, "buildings")));		
			for(String building:features.getBuildings()) {
				if (building == null) continue;
				List<Facility> facilities = features.getFacilitiesByBuilding(building);
				Item i = buildingsSection.add(new Item(building, null));
				
				Directory buildingDirectory = i.setContent(new Directory());
				Section buildingSection = buildingDirectory.add(new Section(building));
				for(Facility f:facilities) {
					try {
						buildingSection.add(new Item(f.getName(),
													 f.getNamePron(),
													 f.getNodeID(),
													 buildingFloorString(f, locale),
													 buildingFloorPronString(f, locale),
													 f.getHulopTags()));
					} catch(Exception e) {
						System.err.println(f);
					}
				}
				buildingDirectory.sortAndDevide(itemComparator, firstLetterIndex);
				buildingDirectory.showSectionIndex = true;
			}
			buildingsSection.sort(itemComparator);		
		}
		if (groupFloor){
			Section floorsSection = this.add(new Section(Messages.get(locale, "floors")));
			Item outdoorItem = floorsSection.add(new Item(Messages.get(locale, "outdoor"), null));
    		Directory outdoorDirectory = outdoorItem.setContent(new Directory());
			Section outdoorSection = outdoorDirectory.add(new Section(Messages.get(locale, "outdoor")));
			for (String floor:features.getFloors()) {
				if (floor == null || floor.isEmpty()) continue;
				List<Facility> facilities = features.getFacilitiesByFloor(floor);
				Item i = floorsSection.add(new Item(floorString(floor, locale), null));

				Directory  floorDirectory = i.setContent(new Directory());
				Section floorSection = floorDirectory.add(new Section(floor));
				for(Facility f:facilities) {
					String hulopTags = f.getHulopTags();
					String hulopMajorCategory = f.getMajorCategory();
					if (hulopMajorCategory != null && hulopMajorCategory.contains("demonstration")) continue;
					Integer inOutValue = nodemap.getInOutValue(f.getNodeID());
					if (inOutValue != null && inOutValue == 1) {
						outdoorSection.add(new Item(
							f.getName(),
							f.getNamePron(),
							f.getNodeID(),
							Messages.get(locale, "outdoor"),
							Messages.get(locale, "outdoor"),
							hulopTags
						));
					} else {
						floorSection.add(new Item(
							f.getName(),
							f.getNamePron(),
							f.getNodeID(),
							buildingFloorString(f, locale),
							buildingFloorPronString(f, locale),
							hulopTags
						));
					}
				}
				floorDirectory.sortAndDevide(itemComparator, firstLetterIndex);
				floorDirectory.showSectionIndex = true;
			}
			outdoorDirectory.sortAndDevide(itemComparator, firstLetterIndex);
			outdoorDirectory.showSectionIndex = true;
			floorsSection.sort(itemComparator);
		}
		if (groupCategory && features.getMajorCategories().length > 0) {
			Section categoriesSection = this.add(new Section(Messages.get(locale, "categories")));		
			for(String category:features.getMajorCategories()) {
				if (category == null || category.isEmpty()) continue;
				List<Facility> facilities = features.getFacilitiesByMajorCategory(category);
				Item i = categoriesSection.add(new Item(Messages.get(locale, category), null));

				Directory categoryDirectory = i.setContent(new Directory());
				Section categorySection = categoryDirectory.add(new Section(Messages.get(locale, category)));
				for(Facility f:facilities) {
					try {
						categorySection.add(new Item(f.getName(),
						                             f.getNamePron(),
													 f.getNodeID(),
													 buildingFloorString(f, locale),
													 buildingFloorPronString(f, locale),
													 f.getHulopTags()));
					} catch(Exception e) {
						System.err.println(f);
					}
				}
				categoryDirectory.sortAndDevide(itemComparator, firstLetterIndex);
				categoryDirectory.showSectionIndex = true;
			}
			categoriesSection.sort(itemComparator);		
		}
		if (groupNearbyFacility) {
			Section serviceSection = this.add(new Section(Messages.get(locale, "nearby_facility")));		
			
			for(Facility service:features.getServices()) {
				serviceSection.add(new Item(service.getName(), service.getNamePron(), service.getNodeID()));
			}
			serviceSection.sort(itemComparator);
		}
	}
	
	protected String floorString(String floor, Locale locale) {
		Double f = Double.parseDouble(floor);
		if (f < 0) {
			return Messages.get(locale, "floor_below_ground")
						  .replace("{0}", String.valueOf(-f.intValue()));
		} else {
			return Messages.get(locale, "floor_above_ground")
						  .replace("{0}", String.valueOf(f.intValue()));
		}
	}
	protected String buildingFloorString(Facility facility, Locale locale) {
		try {
			Double f = Double.parseDouble(facility.getFloor());
			if (f < 0) {
				return facility.getBuilding() + " - " +
					   Messages.get(locale, "floor_below_ground")
							   .replace("{0}", String.valueOf(-f.intValue()));
			} else {
				return facility.getBuilding() + " - " +
					   Messages.get(locale, "floor_above_ground")
							   .replace("{0}", String.valueOf(f.intValue()));
			}
		} catch (Exception e) {
			return facility.getBuilding();
		}
	}
	protected String buildingFloorPronString(Facility facility, Locale locale) {
		return buildingFloorString(facility, locale);
	}
	protected String buildingRoomFloorString(Facility facility, Locale locale) {
		return facility.getName() + " (" + buildingFloorString(facility, locale) + ")";
	}
	protected String buildingRoomFloorPronString(Facility facility, Locale locale) {
		return facility.getName() + " (" + buildingFloorPronString(facility, locale) + ")";
	}
	

	public void setLandmakrks(JSONArray landmarks) {
		this.landmarks = landmarks;
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		Directory dir = (Directory) super.clone();
		dir.sections = new ArrayList<Section>();
		for(Section s:sections) {
			dir.sections.add((Section)s.clone());
		}
		return dir;
	}

	public void sortAndDevide(Comparator<Item> compare, IndexOperator indexOperator) {
		if (sections.size() > 1) {
			throw new RuntimeException("section size should be one to operate");
		}
		Section section = sections.get(0);
		section.sort(compare);
		ArrayList<Section> tempSections = new ArrayList<Section>();
		Section tempSection = null;
		String lastIndex = null;
		for(Item item: section.items) {
			String index = indexOperator.getIndex(item);
			if (!index.equals(lastIndex)) {
				tempSection = new Section(index);
				tempSections.add(tempSection);
			}
			tempSection.add(item);
			lastIndex = index;
		}
		sections = tempSections;
	}

	public interface IndexOperator {
		public String getIndex(Item item);
	}
	
	public class FirstLetterIndex implements IndexOperator {
		public String getIndex(Item item) {
			if (item.sortKey != null) {
				return item.sortKey.substring(0, 1).toUpperCase();
			}
			if (item.title != null) {
				return item.title.substring(0, 1).toUpperCase();
			}
			return "*";
		}
	}
	
	public Section add(Section section) {
		this.sections.add(section);
		return section;
	}

	public class Section implements Cloneable {
		@Override
		protected Object clone() throws CloneNotSupportedException {
			Section section = (Section) super.clone();
			section.items = new ArrayList<Item>();
			for(Item i:items) {
				section.items.add((Item)i.clone());
			}
			return section;
		}

		public Section() {			
		}
		public void sort(Comparator<Item> compare) {
			items.sort(compare);
		}
		public Section(String title) {
			this.title = title;
		}
		public Section(String title, String titlePron, String indexTitle) {
			this(title);
			this.titlePron = titlePron;
			this.indexTitle = indexTitle;
		}
		
		private String title;
		private String titlePron;
		private String indexTitle;
		private ArrayList<Item> items = new ArrayList<Item>();
		
		public Item add(Item item) {
			items.add(item);
			return item;
		}
		
		public JSONObject toJSON() throws JSONException {
			JSONObject section = new JSONObject();
			if (title != null) section.put("title", title);
			if (titlePron != null) section.put("pron", titlePron);
			if (indexTitle != null) section.put("indexTitle", indexTitle);

			for(Item i: this.items) {
				section.append("items", i.toJSON());
			}
			return section;
		}

		public void walk(String query, ArrayList<Item> buffer) {
			for(Item item:items) {
				item.walk(query, buffer);
			}
		}
	}
	
	public class Item implements Cloneable{
		@Override
		protected Object clone() throws CloneNotSupportedException {
			Item item = (Item) super.clone();
			item.content = (Directory)content.clone();
			return item;
		}

		public void walk(String query, ArrayList<Item> buffer) {
			if (content != null) {
				content.walk(query, buffer);
			} else {
				if (matches(query)) {
					buffer.add(this);
				}
			}
		}

		private boolean matches(String query) {
			String lower = query.toLowerCase();
			return (title != null && title.toLowerCase().indexOf(lower) > -1) ||
					(titlePron != null && titlePron.toLowerCase().indexOf(lower) > -1) ||
					(subtitle != null && subtitle.toLowerCase().indexOf(lower) > -1);
		}

		public Item(String title, String titlePron) {
			if (title == null) { throw new RuntimeException("no title"); }
			this.title = title;
			this.titlePron = titlePron;
		}
		public Item(String title, String titlePron, String nodeID) {
			this(title, titlePron);
			this.nodeID = nodeID;
		}
		public Item(String title, String titlePron, String nodeID, String subtitle, String subtitlePron) {
			this(title, titlePron, nodeID);
			this.subtitle = subtitle;
			this.subtitlePron = subtitlePron;
		}
		public Item(String title, String titlePron, String nodeID, String subtitle, String subtitlePron, String hulopTags) {
			this(title, titlePron, nodeID, subtitle, subtitlePron);
			this.forDemonstration = "false";
			if (hulopTags != null && hulopTags.contains("demo")) {
				this.forDemonstration = "true";
			}
		}
		public int compare(Item item) {
			if (sortKey != null && item.sortKey != null) {
				return sortKey.compareToIgnoreCase(item.sortKey);
			}
			return title.compareToIgnoreCase(item.title);
		}
		private String title;
		private String titlePron;
		private Directory content;
		private String nodeID;
		private String subtitle;
		private String subtitlePron;
		private String forDemonstration;
		
		public String sortKey;
		
		public Directory setContent(Directory content) {
			this.content = content;
			return content;
		}
		
		public JSONObject toJSON() throws JSONException {
			JSONObject item = new JSONObject();
			if (title != null) item.put("title", title);
			if (titlePron != null) item.put("titlePron", titlePron);
			if (nodeID != null) item.put("nodeID", nodeID);
			if (content != null) item.put("content", content.toJSON());
			if (subtitle != null) item.put("subtitle", subtitle);
			if (subtitlePron != null) item.put("subtitlePron", subtitlePron);
			if (forDemonstration != null) item.put("forDemonstration", forDemonstration);

			return item;
		}
	}
	
	public JSONObject toJSON() throws JSONException {
		JSONObject directory = new JSONObject();
		for(Section s: this.sections) {
			directory.append("sections", s.toJSON());
		}
		directory.put("showSectionIndex", showSectionIndex);
		if (landmarks != null) directory.put("landmarks", landmarks);
		return directory;
	}

	public void walk(String query, ArrayList<Item> buffer) {
		for(Section s:sections) {
			s.walk(query, buffer);
		}
	}

	@Override
	public Directory search(String query) {
		Directory result = new Directory();
		Section section = result.add(new Section());
		walk(query, section.items);
		return result;
	}
	

	public static void main(String[] args) throws MalformedURLException, JSONException {
		String host = "localhost:9090/map";
		String lat = null;
		String lng = null;
		try {
			URL url = new URL("http://"+host+"/api/config");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("HTTP error code: " + conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
			br.close();
			conn.disconnect();
			JSONObject config = new JSONObject(response.toString());
            lat = config.getJSONObject("INITIAL_LOCATION").getString("lat");
            lng = config.getJSONObject("INITIAL_LOCATION").getString("lng");
            System.out.println("Latitude: " + lat);
            System.out.println("Longitude: " + lng);
        } catch (Exception e) {
            e.printStackTrace();
        }
		String user = "test-user";
		String dist = "5000";
		String lang = "ja";
		Boolean enableGroupBuilding = true;
		Boolean enableGroupFloor = true;
		Boolean enableGroupCategory = true;
		Boolean enableGroupNearbyFacility = false;
		String featuresUrlstr = String.format("http://%s/routesearch?action=start&cache=false&lat=%s&lng=%s&user=%s&dist=%s&lang=%s", host, lat, lng, user, dist, lang);
		String nodemapUrlString = String.format("http://%s/routesearch?action=nodemap&cache=false&lat=%s&lng=%s&user=%s&dist=%s&lang=%s", host, lat, lng, user, dist, lang);
		URL featuresUrl = new URL(featuresUrlstr);
		URL nodemapUrl = new URL(nodemapUrlString);
		Directory d = new Directory(featuresUrl, nodemapUrl, new Locale(lang), enableGroupBuilding, enableGroupFloor, enableGroupCategory, enableGroupNearbyFacility);
		walk(d.toJSON(), 0, 5);
	}
	// utility function
	static void walk(Object obj, int level, int max_level) throws JSONException {
		if (level > max_level) {
			return;
		}
		if (obj instanceof JSONArray) {
			JSONArray array = (JSONArray) obj;
			for (Object item : array) {
				walk(item, level, max_level);
			}
			return;
		}

		String indent = "";
		for (int i = 0; i < level; i++ ) {
			indent += "  ";
		}
		if (obj instanceof JSONObject) {
			JSONObject jobj = (JSONObject) obj;

			if (jobj.has("title")) {
				String title = jobj.getString("title");
				System.out.println(String.format("%s- %s", indent, title));

				if (jobj.has("items")) {
					JSONArray items = jobj.getJSONArray("items");
					for (Object item : items) {
						walk(item, level + 1, max_level);
					}
				}
				else if (jobj.has("content")) {
					walk(jobj.get("content"), level + 1, max_level);
				}
			}
			else if (jobj.has("name")) {
				String name = jobj.getString("name");
				System.out.println(String.format("%s* %s", indent, name));
			}
			else if (jobj.has("sections")) {
				JSONArray items = jobj.getJSONArray("sections");
				for (Object item : items) {
					walk(item, level, max_level);
				}
			}
			else {
				for (Object key : jobj.keySet()) {
					System.out.println(String.format("%s+ %s", indent, key));
					walk(jobj.get(key), level + 1, max_level);
				}
			}
		}
	}
}
