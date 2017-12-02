package pl.shockah.pintail;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class BasicPluginManager extends PluginManager<PluginInfo, BasicPluginManager, BasicPlugin> {
	public BasicPluginManager() {
		super(PluginInfo.class, BasicPlugin.class);
	}

	public BasicPluginManager(@Nonnull Path pluginsPath) {
		super(PluginInfo.class, BasicPlugin.class, pluginsPath);
	}
}