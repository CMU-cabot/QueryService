package org.hulop.data.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.wink.json4j.JSONObject;

public class Messages {
	private static final long CACHE_EXPIRY = 60000;
	private static Map<String, Map<String, String>> remoteTranslations = new ConcurrentHashMap<>();
	private static long lastFetchedTime = 0;

	private static final URL keyConfigUrl = initKeyConfigUrl();
	private static URL initKeyConfigUrl() {
		try {
			String use_http = System.getenv("HULOP_MAP_SERVICE_USE_HTTP");
			String protocol = ("true".equals(use_http)) ? "http" : "https";
			String mapService = System.getenv("HULOP_MAP_SERVICE");
			String keyConfigUrlString = String.format("%s://%s/map/hulop_messages.json", protocol, mapService);
			return new URL(keyConfigUrlString);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static ResourceBundle.Control control = new ResourceBundle.Control() {
		public static final String XML = "xml";
		public List<String> getFormats(String baseName) {
			return Arrays.asList(XML);
		}
		public ResourceBundle newBundle(String baseName, Locale locale,
				String format, ClassLoader loader, boolean reload)
				throws IllegalAccessException, InstantiationException, IOException {
			if (baseName == null || locale == null || format == null
					|| loader == null) {
				throw new NullPointerException();
			}

			ResourceBundle bundle = null;
			if (format.equals(XML)) {
				String bundleName = toBundleName(baseName, locale);
				String resourceName = toResourceName(bundleName, format);
				InputStream stream = null;
				URL url = loader.getResource(resourceName);

				if (url != null) {
					URLConnection connection = url.openConnection();
					if (connection != null) {
						if (reload) {
							connection.setUseCaches(false);
						}
						stream = connection.getInputStream();
					}
				}

				if (stream != null) {
					Properties props = new Properties();
					props.loadFromXML(stream);

					return new ResourceBundle() {
						@Override
						protected Object handleGetObject(String key) {
							return props.getProperty(key);
						}
						@SuppressWarnings("unchecked")
						@Override
						public Enumeration<String> getKeys() {
							return (Enumeration<String>) props.propertyNames();
						}
					};
				}
			}
			return bundle;
		}

		@Override
		public Locale getFallbackLocale(String baseName, Locale locale) {
			return null;
		}
	};
	
	
	public static ResourceBundle getBundle(Locale locale) {
		return ResourceBundle.getBundle("org.hulop.data.i18n.Messages", locale, control);
	}
	
	public static String get(Locale locale, String key) {
		if (isRemoteKeyPresent(locale, key)) {
			return getFromRemote(locale, key);
		}

		ResourceBundle resource = getBundle(locale);
		if (resource != null) {
			try {
				return resource.getString(key);
			} catch (Exception e) {
				return "___" + key + "___no_entry";
			}
		}
		return "___" + key + "___no_resource";
	}

	private static boolean isRemoteKeyPresent(Locale locale, String key) {
		if (System.currentTimeMillis() - lastFetchedTime > CACHE_EXPIRY) {
			fetchRemoteTranslations();
		}

		Map<String, String> translations = remoteTranslations.get(key);
		return translations != null && translations.containsKey(locale.getLanguage());
	}

	private static String getFromRemote(Locale locale, String key) {
		Map<String, String> translations = remoteTranslations.get(key);
		return translations.get(locale.getLanguage());
	}

	private static void fetchRemoteTranslations() {
        try {
            HttpURLConnection conn = (HttpURLConnection) keyConfigUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONObject queryServiceKeys = jsonObject.getJSONObject("query_service_keys");

                    remoteTranslations.clear();
                    for (Object keyObj : queryServiceKeys.keySet()) {
						String key = keyObj.toString();
                        JSONObject value = queryServiceKeys.getJSONObject(key);

                        Map<String, String> translations = new ConcurrentHashMap<>();
                        for (Object langKeyObj : value.keySet()) {
							String langKey = langKeyObj.toString();
                            translations.put(langKey, value.getString(langKey));
                        }
                        remoteTranslations.put(key, translations);
                    }
                    lastFetchedTime = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
