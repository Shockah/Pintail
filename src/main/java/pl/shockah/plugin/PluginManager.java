package pl.shockah.plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import pl.shockah.json.JSONObject;
import pl.shockah.json.JSONParser;
import pl.shockah.util.FileUtils;
import pl.shockah.util.ReadWriteList;
import pl.shockah.util.UnexpectedException;

public class PluginManager<M extends PluginManager<M, P>, P extends Plugin<M, P>> {
	public static final Path LIBS_PATH = Paths.get("libs");
	public static final Path PLUGINS_PATH = Paths.get("plugins");
	
	protected final Class<P> clazz;
	protected final Path pluginsPath;
	protected final Path libsPath;
	
	public ClassLoader pluginClassLoader = null;
	public ReadWriteList<Plugin.Info> pluginInfos = new ReadWriteList<>(new ArrayList<>());
	public ReadWriteList<P> plugins = new ReadWriteList<>(new ArrayList<>());
	
	public PluginManager(Class<P> clazz) {
		this(clazz, PLUGINS_PATH, LIBS_PATH);
	}
	
	public PluginManager(Class<P> clazz, Path pluginsPath, Path libsPath) {
		this.clazz = clazz;
		this.pluginsPath = pluginsPath;
		this.libsPath = libsPath;
	}
	
	public Path getPluginPath() {
		return pluginsPath;
	}
	
	public Path getLibsPath() {
		return libsPath;
	}
	
	public void reload() {
		unload();
		load();
	}
	
	protected void unload() {
		plugins.readOperation(plugins -> {
			List<P> reversed = new ArrayList<>(plugins);
			Collections.reverse(reversed);
			for (P plugin : reversed) {
				plugin.onUnload();
				onPluginUnload(plugin);
			}
		});
		plugins.clear();
		pluginInfos.clear();
		
		pluginClassLoader = null;
	}
	
	protected void load() {
		List<Plugin.Info> infos = findPlugins();
		infos = dependencySort(infos);
		pluginInfos.addAll(infos);
		pluginClassLoader = createClassLoader(pluginInfos);
		
		pluginInfos.iterate(pluginInfo -> {
			if (shouldEnable(pluginInfo)) {
				P plugin = loadPlugin(pluginClassLoader, pluginInfo);
				if (plugin != null) {
					try {
						setupRequiredDependencyFields(plugin);
						plugin.onLoad();
						plugins.add(plugin);
						onPluginLoad(plugin);
					} catch (Exception e) {
						throw new UnexpectedException(e);
					}
				}
			}
		});
		
		plugins.iterate(plugin -> {
			setupOptionalDependencyFields(plugin);
		});
		
		plugins.iterate(plugin -> {
			plugin.onAllPluginsLoaded();
		});
	}
	
	protected void onPluginLoad(P plugin) {
		System.out.println(String.format("Loaded plugin: %s", plugin.info.packageName()));
	}
	
	protected void onPluginUnload(P plugin) {
		System.out.println(String.format("Unloaded plugin: %s", plugin.info.packageName()));
	}
	
	@SuppressWarnings("unchecked")
	protected P loadPlugin(ClassLoader classLoader, Plugin.Info info) {
		try {
			Class<?> clazz = classLoader.loadClass(info.baseClass());
			for (Constructor<?> ctor : clazz.getConstructors()) {
				Class<?>[] params = ctor.getParameterTypes();
				if (params.length == 2 && getClass().isAssignableFrom(params[0]) && params[1] == Plugin.Info.class) {
					return (P)ctor.newInstance(this, info);
				}
			}
			throw new NoSuchMethodException("Missing plugin constructor.");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void setupRequiredDependencyFields(P plugin) {
		for (Field field : plugin.getClass().getDeclaredFields()) {
			try {
				Plugin.Dependency dependencyAnnotation = field.getAnnotation(Plugin.Dependency.class);
				if (dependencyAnnotation != null) {
					if (dependencyAnnotation.value().equals("")) {
						Class<? extends P> clazz = (Class<? extends P>)field.getType();
						if (clazz == this.clazz)
							continue;
						P dependency = getPluginWithClass(clazz);
						if (dependency != null) {
							field.setAccessible(true);
							field.set(plugin, dependency);
							plugin.onDependencyLoaded(plugin);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	protected void setupOptionalDependencyFields(P plugin) {
		for (Field field : plugin.getClass().getDeclaredFields()) {
			try {
				Plugin.Dependency dependencyAnnotation = field.getAnnotation(Plugin.Dependency.class);
				if (dependencyAnnotation != null) {
					if (!dependencyAnnotation.value().equals("")) {
						P dependency = getPluginWithPackageName(dependencyAnnotation.value());
						if (dependency != null) {
							field.setAccessible(true);
							field.set(plugin, dependency);
							plugin.onDependencyLoaded(plugin);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public <P2> P2 getPluginWithClass(Class<P2> clazz) {
		return (P2)plugins.filterFirst(plugin -> clazz.isInstance(plugin));
	}
	
	@SuppressWarnings("unchecked")
	public <P2> List<P2> getPluginsWithClass(Class<P2> clazz) {
		List<P2> ret = new ArrayList<>();
		plugins.iterate(plugin -> {
			if (clazz.isInstance(plugin))
				ret.add((P2)plugin);
		});
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	public <P2 extends P> P2 getPluginWithPackageName(String name) {
		return (P2)plugins.filterFirst(plugin -> plugin.info.packageName().equals(name));
	}
	
	protected List<Plugin.Info> findPlugins() {
		List<Plugin.Info> infos = new ArrayList<>();
		
		try {
			for (Path path : Files.newDirectoryStream(getPluginPath(), path -> path.getFileName().toString().endsWith(".jar"))) {
				Path tmpPath = FileUtils.copyAsTrueTempFile(path);
				
				try (ZipFile zf = new ZipFile(tmpPath.toFile())) {
					ZipEntry ze = zf.getEntry("plugin.json");
					if (ze == null)
						continue;
					
					JSONObject pluginJson = new JSONParser().parseObject(new String(IOUtils.toByteArray(zf.getInputStream(ze)), "UTF-8"));
					infos.add(new Plugin.Info(pluginJson, tmpPath.toUri().toURL()));
				} catch (Exception e) {
					throw new UnexpectedException(e);
				}
			}
		} catch (Exception e) {
			throw new UnexpectedException(e);
		}
		
		return infos;
	}
	
	protected List<Plugin.Info> dependencySort(List<Plugin.Info> input) {
		input = new ArrayList<>(input);
		List<Plugin.Info> output = new ArrayList<>(input.size());
		List<String> loadedPackageNames = new ArrayList<>(input.size());
		
		while (!input.isEmpty()) {
			int oldSize = input.size();
			
			for (int i = 0; i < input.size(); i++) {
				Plugin.Info info = input.get(i);
				
				boolean allDependenciesLoaded = true;
				for (String dependency : info.dependsOn()) {
					if (!loadedPackageNames.contains(dependency)) {
						allDependenciesLoaded = false;
						break;
					}
				}
				if (allDependenciesLoaded) {
					loadedPackageNames.add(info.packageName());
					output.add(info);
					input.remove(i--);
				}
			}
			
			if (oldSize == input.size()) {
				//TODO: log plugins with missing dependencies (the ones left in $input)
				break;
			}
		}
		
		return output;
	}
	
	protected ClassLoader createClassLoader(ReadWriteList<Plugin.Info> infos) {
		List<URL> urls = new ArrayList<>();
		try {
			for (Path path : Files.newDirectoryStream(getLibsPath(), path -> path.getFileName().toString().endsWith(".jar"))) {
				Path tmpPath = FileUtils.copyAsTrueTempFile(path);
				urls.add(tmpPath.toUri().toURL());
			}
		} catch (Exception e) {
			throw new UnexpectedException(e);
		}
		infos.iterate(info -> urls.add(info.url));
		return new URLClassLoader(urls.toArray(new URL[0]));
	}
	
	protected boolean shouldEnable(Plugin.Info info) {
		return info.enabledByDefault();
	}
}