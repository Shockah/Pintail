package pl.shockah.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import pl.shockah.json.JSONObject;

public class Plugin<M extends PluginManager<M, P>, P extends Plugin<M, P>> {
	public final M manager;
	public final Info info;
	
	public Plugin(M manager, Info info) {
		this.manager = manager;
		this.info = info;
	}
	
	protected void onLoad() {
	}
	
	protected void onUnload() {
	}
	
	protected void onDependencyLoaded(P plugin) {
	}
	
	protected void onDependencyUnloaded(P plugin) {
	}
	
	protected void onAllPluginsLoaded() {
	}
	
	public static final class Info {
		public final JSONObject json;
		public final URL url;
		
		public Info(JSONObject json, URL url) {
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
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public static @interface Dependency {
		public String value() default "";
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface ClassLoaderProvider {
		public String value();
	}
}