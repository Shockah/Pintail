package pl.shockah.plugin;

import pl.shockah.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class PluginInfo {
	@Nonnull public final JSONObject json;
	@Nonnull public final URL url;

	public PluginInfo(@Nonnull JSONObject json, @Nonnull URL url) {
		this.json = json;
		this.url = url;
	}

	@Nonnull public String getPackageName() {
		return json.getString("packageName");
	}

	@Nonnull public String getBaseClass() {
		return json.getString("baseClass");
	}

	@Nonnull public List<String> getDependencies() {
		return Collections.unmodifiableList(json.getListOrEmpty("dependsOn").ofStrings());
	}

	public boolean isEnabledByDefault() {
		return json.getBool("enabledByDefault", true);
	}

	@Nonnull public String getName() {
		return json.getString("name", getPackageName());
	}

	@Nullable public String getAuthor() {
		return json.getOptionalString("author");
	}

	@Nullable public String getDescription() {
		return json.getOptionalString("description");
	}

	@Nonnull public String getClassLoader() {
		return json.getString("classLoader", "default");
	}
}