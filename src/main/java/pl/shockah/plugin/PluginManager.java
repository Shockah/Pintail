package pl.shockah.plugin;

import org.apache.commons.io.IOUtils;
import pl.shockah.json.JSONObject;
import pl.shockah.json.JSONParser;
import pl.shockah.util.FileUtils;
import pl.shockah.util.ReadWriteList;
import pl.shockah.util.ReadWriteMap;
import pl.shockah.util.UnexpectedException;
import pl.shockah.util.func.Func2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PluginManager<I extends PluginInfo, M extends PluginManager<I, M, P>, P extends Plugin<I, M, P>> {
	public static final Path PLUGINS_PATH = Paths.get("plugins");

	protected final Class<I> pluginInfoClass;
	protected final Class<P> pluginClass;
	protected final Path pluginsPath;
	
	public ClassLoader pluginClassLoader = null;
	public ReadWriteList<I> pluginInfos = new ReadWriteList<>(new ArrayList<>());
	public ReadWriteList<P> plugins = new ReadWriteList<>(new ArrayList<>());
	
	protected ReadWriteMap<String, Func2<ClassLoader, URL[], URLClassLoader>> customClassLoaderProviders = new ReadWriteMap<>(new HashMap<>());
	protected ReadWriteMap<String, URLClassLoader> customClassLoaders = new ReadWriteMap<>(new HashMap<>());
	
	public PluginManager(Class<I> pluginInfoClass, Class<P> pluginClass) {
		this(pluginInfoClass, pluginClass, PLUGINS_PATH);
	}
	
	public PluginManager(Class<I> pluginInfoClass, Class<P> pluginClass, Path pluginsPath) {
		this.pluginInfoClass = pluginInfoClass;
		this.pluginClass = pluginClass;
		this.pluginsPath = pluginsPath;
	}
	
	public Path getPluginPath() {
		return pluginsPath;
	}
	
	public void reloadAll() {
		unloadAll();
		loadAll();
	}
	
	protected void unloadAll() {
		plugins.writeOperation(plugins -> {
			List<P> reversed = new ArrayList<>(plugins);
			Collections.reverse(reversed);
			for (P plugin : reversed) {
				unloadInternal(plugin);
			}

			customClassLoaders.clear();
			customClassLoaderProviders.clear();
			plugins.clear();
			pluginInfos.clear();
			pluginClassLoader = null;
		});
	}
	
	protected void loadAll() {
		plugins.writeOperation(plugins -> {
			List<I> infos = findPlugins();
			pluginInfos.addAll(infos);
			pluginClassLoader = createClassLoader(pluginInfos);

			for (I pluginInfo : infos) {
				load(pluginInfo);
			}

			plugins.forEach(this::setupOptionalDependencyFields);
			plugins.forEach(Plugin::onAllPluginsLoaded);
		});
	}

	public void load(I pluginInfo) {
		load(pluginInfo, true);
	}

	public void load(I pluginInfo, boolean withDependencies) {
		plugins.writeOperation(plugins -> {
			if (getPlugin(pluginInfo) != null)
				return;

			for (String dependency : pluginInfo.dependsOn()) {
				if (getPlugin(dependency) == null) {
					if (withDependencies)
						load(getPluginInfo(dependency), true);
					else
						throw new IllegalStateException(String.format(
								"Plugin `%s` cannot be loaded without loading plugin `%s`.",
								pluginInfo.packageName(),
								dependency
						));
				}
			}
			loadInternal(pluginInfo);
		});
	}

	private void loadInternal(I pluginInfo) {
		if (shouldEnable(pluginInfo)) {
			P plugin = loadPlugin(pluginClassLoader, pluginInfo);
			if (plugin != null) {
				try {
					setupClassLoaderProviders(plugin);
					setupRequiredDependencyFields(plugin);
					plugin.onLoad();
					plugins.add(plugin);
					onPluginLoad(plugin);
				} catch (Exception e) {
					throw new UnexpectedException(e);
				}
			}
		}
	}

	public void unload(P plugin) {
		unload(plugin, true);
	}

	public void unload(P plugin, boolean withDependants) {
		plugins.writeOperation(plugins -> {
			for (P anyPlugin : plugins) {
				if (anyPlugin == plugin)
					continue;
				if (anyPlugin.loadedDependencies.contains(plugin)) {
					if (withDependants)
						unload(anyPlugin, true);
					else
						throw new IllegalStateException(String.format(
								"Plugin `%s` cannot be unloaded without unloading plugin `%s`.",
								plugin.info.packageName(),
								anyPlugin.info.packageName()
						));
				}
			}
			unloadInternal(plugin);
		});
	}

	private void unloadInternal(P plugin) {
		plugin.onUnload();
		onPluginUnload(plugin);
	}
	
	protected void onPluginLoad(P plugin) {
		System.out.println(String.format("Loaded plugin: %s", plugin.info.packageName()));
	}
	
	protected void onPluginUnload(P plugin) {
		System.out.println(String.format("Unloaded plugin: %s", plugin.info.packageName()));
	}
	
	@SuppressWarnings("unchecked")
	protected P loadPlugin(ClassLoader classLoader, I info) {
		try {
			String infoClassLoader = info.classLoader();
			if (!infoClassLoader.equals("default"))
				customClassLoaders.computeIfAbsent(infoClassLoader, key -> customClassLoaderProviders.get(key).call(classLoader, createURLArray(pluginInfos)));
			
			Class<?> clazz = classLoader.loadClass(info.baseClass());
			for (Constructor<?> ctor : clazz.getConstructors()) {
				Class<?>[] params = ctor.getParameterTypes();
				if (params.length == 2 && getClass().isAssignableFrom(params[0]) && params[1] == pluginInfoClass)
					return (P)ctor.newInstance(this, info);
			}
			throw new NoSuchMethodException("Missing plugin constructor.");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	protected void setupClassLoaderProviders(P plugin) {
		for (Method method : plugin.getClass().getDeclaredMethods()) {
			try {
				Plugin.ClassLoaderProvider classLoaderProviderAnnotation = method.getAnnotation(Plugin.ClassLoaderProvider.class);
				if (classLoaderProviderAnnotation != null) {
					customClassLoaderProviders.put(classLoaderProviderAnnotation.value(), (cl, urls) -> {
						try {
							return (URLClassLoader)method.invoke(plugin, cl, urls);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							throw new UnexpectedException(e);
						}
					});
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
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
						if (clazz == pluginClass)
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
						P dependency = getPlugin(dependencyAnnotation.value());
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
		return (P2)plugins.filterFirst(clazz::isInstance);
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

	public I getPluginInfo(String packageName) {
		return pluginInfos.filterFirst(pluginInfo -> pluginInfo.packageName().equals(packageName));
	}
	
	@SuppressWarnings("unchecked")
	public <P2 extends P> P2 getPlugin(String packageName) {
		return (P2)plugins.filterFirst(plugin -> plugin.info.packageName().equals(packageName));
	}

	public <P2 extends P> P2 getPlugin(PluginInfo pluginInfo) {
		return getPlugin(pluginInfo.packageName());
	}

	@SuppressWarnings("unchecked")
	protected List<I> findPlugins() {
		List<I> infos = new ArrayList<>();
		
		try {
			for (Path path : Files.newDirectoryStream(getPluginPath(), path -> path.getFileName().toString().endsWith(".jar"))) {
				Path tmpPath = FileUtils.copyAsTrueTempFile(path);
				
				try (ZipFile zf = new ZipFile(tmpPath.toFile())) {
					ZipEntry ze = zf.getEntry("plugin.json");
					if (ze == null)
						continue;
					
					JSONObject pluginJson = new JSONParser().parseObject(new String(IOUtils.toByteArray(zf.getInputStream(ze)), "UTF-8"));
					I pluginInfo = (I)pluginInfoClass.getConstructors()[0].newInstance(pluginJson, tmpPath.toUri().toURL());
					infos.add(pluginInfo);
				} catch (Exception e) {
					throw new UnexpectedException(e);
				}
			}
		} catch (Exception e) {
			throw new UnexpectedException(e);
		}
		
		return infos;
	}
	
	protected URL[] createURLArray(ReadWriteList<I> infos) {
		List<URL> urls = new ArrayList<>();
		infos.iterate(info -> urls.add(info.url));
		return urls.toArray(new URL[0]);
	}
	
	protected ClassLoader createClassLoader(ReadWriteList<I> infos) {
		return new URLClassLoader(createURLArray(infos));
	}
	
	protected boolean shouldEnable(I info) {
		return info.enabledByDefault();
	}
}