package pl.shockah.plugin;

import pl.shockah.json.JSONObject;

import java.net.URL;
import java.util.Collections;
import java.util.List;

public class PluginInfo {
	public final JSONObject json;
	public final URL url;

	public PluginInfo(JSONObject json, URL url) {
		this.json = json;
		this.url = url;
	}

	public String packageName() {
		return json.getString("packageName");
	}

	public String baseClass() {
		return json.getString("baseClass");
	}

	public List<String> dependsOn() {
		return Collections.unmodifiableList(json.getListOrEmpty("dependsOn").ofStrings());
	}

	public boolean enabledByDefault() {
		return json.getBool("enabledByDefault", true);
	}

	public String name() {
		return json.getString("name", packageName());
	}

	public String author() {
		return json.getString("author", null);
	}

	public String description() {
		return json.getString("description", null);
	}

	public String classLoader() {
		return json.getString("classLoader", "default");
	}
}