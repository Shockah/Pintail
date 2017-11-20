package pl.shockah.pintail;

import javax.annotation.Nonnull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

public class Plugin<I extends PluginInfo, M extends PluginManager<I, M, P>, P extends Plugin<I, M, P>> {
	@Nonnull public final M manager;
	@Nonnull public final I info;

	@Nonnull final List<P> loadedRequiredDependencies = new ArrayList<>();
	@Nonnull final List<P> loadedOptionalDependencies = new ArrayList<>();
	@Nonnull final List<P> requiredDependants = new ArrayList<>();
	@Nonnull final List<P> optionalDependants = new ArrayList<>();
	
	public Plugin(@Nonnull M manager, @Nonnull I info) {
		this.manager = manager;
		this.info = info;
	}
	
	protected void onUnload() {
	}
	
	protected void onDependencyLoaded(@Nonnull P plugin) {
	}
	
	protected void onDependencyUnloaded(@Nonnull I info) {
	}
	
	protected void onAllPluginsLoaded() {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public @interface RequiredDependency {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface OptionalDependency {
		String value();
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface ClassLoaderProvider {
		String value();
	}
}