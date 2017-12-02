# Pintail

Generic plugin management library.

# Gradle setup

```gradle
repositories {
	maven {
		url 'https://jitpack.io'
	}
}

dependencies {
	compile 'com.github.Shockah:Pintail:1.6.1'
}
```

# Usage

Some managing class:

```java
BasicPluginManager pluginManager = new BasicPluginManager("plugins");
pluginManager.reloadAll()
```

MyPlugin.java:

```java
package mycompany.myplugin;

// ...

public class MyPlugin extends BasicPlugin {
	public MyPlugin(BasicPluginManager manager, PluginInfo info) {
		super(manager, info);
		System.out.println("Plugin is being loaded: " + info.getPackageName());
	}

	@Override
	protected void onUnload() {
		System.out.println("Plugin is being unloaded: " + info.getPackageName());
	}
}
```

plugin.json:

```json
{
	"packageName": "mycompany.myplugin",
	"baseClass": "mycompany.myplugin.MyPlugin",
	"enabledByDefault": true,

	"name": "My very own plugin",
	"author": "Michael Dolas",
	"description": "It's my first plugin! It literally does nothing though."
}
```

## Handling dependencies

### Required dependencies

Required dependencies have to be defined in the plugin.json file.

```
{
	// ...
	"dependsOn": [
		"mycompany.anotherplugin" // the `packageName` of the other plugin
	],
	// ...
}
```

After doing so the Plugin subclass can be modified like this:

```java
// ...

public class MyPlugin extends BasicPlugin {
	private final AnotherPlugin anotherPlugin;

	public MyPlugin(BasicPluginManager manager, PluginInfo info, @RequiredDependency AnotherPlugin anotherPlugin) {
		super(manager, info);
		this.anotherPlugin = anotherPlugin;
	}

	// ...
}
```

### Optional dependencies

Optional dependencies only require a change in the Plugin subclass.

```java
// ...

public class MyPlugin extends BasicPlugin {
	private final BasicPlugin anotherPlugin; // the class cannot be used directly, because if the dependency is missing, the plugin will just throw an exception

	public MyPlugin(BasicPluginManager manager, PluginInfo info, @OptionalDependency("mycompany.anotherplugin") BasicPlugin optionalAnotherPlugin) {
		super(manager, info);
		this.anotherPlugin = optionalAnotherPlugin;

		if (optionalAnotherPlugin != null) {
			// it's safe to use the dependency here
			AnotherPlugin anotherPlugin = (AnotherPlugin)optionalAnotherPlugin;
			// use the plugin here
		}
	}

	// ...
}
```

## ClassLoader providers

Plugins have the ability to provide additional ClassLoaders. An example would be a [Groovy](http://groovy-lang.org/) plugin providing a ClassLoader allowing plugins written in Groovy to be loaded.

An example can be found here: [GroovyPlugin.java](https://github.com/Shockah/Dunlin/blob/2.0/Dunlin%20-%20Groovy/src/main/java/pl/shockah/dunlin/groovy/GroovyPlugin.java)
