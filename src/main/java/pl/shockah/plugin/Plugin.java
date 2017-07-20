package pl.shockah.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

public class Plugin<I extends PluginInfo, M extends PluginManager<I, M, P>, P extends Plugin<I, M, P>> {
	public final M manager;
	public final I info;
	final List<P> loadedDependencies = new ArrayList<>();
	
	public Plugin(M manager, I info) {
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
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Dependency {
		String value() default "";
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface ClassLoaderProvider {
		String value();
	}
}