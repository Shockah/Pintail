package pl.shockah.pintail;

import javax.annotation.Nonnull;

public class BasicPlugin extends Plugin<PluginInfo, BasicPluginManager, BasicPlugin> {
	public BasicPlugin(@Nonnull BasicPluginManager manager, @Nonnull PluginInfo info) {
		super(manager, info);
	}
}