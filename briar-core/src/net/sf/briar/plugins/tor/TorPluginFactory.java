package net.sf.briar.plugins.tor;

import java.util.concurrent.Executor;

import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.util.StringUtils;

public class TorPluginFactory implements DuplexPluginFactory {

	private static final long MAX_LATENCY = 5 * 60 * 1000; // 5 minutes
	private static final long POLLING_INTERVAL = 15 * 60 * 1000; // 15 minutes

	private final Executor pluginExecutor;

	public TorPluginFactory(@PluginExecutor Executor pluginExecutor) {
		this.pluginExecutor = pluginExecutor;
	}

	public TransportId getId() {
		return TorPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		// This plugin is not enabled by default
		String enabled = callback.getConfig().get("enabled");
		if(StringUtils.isNullOrEmpty(enabled)) return null;
		return new TorPlugin(pluginExecutor, callback, MAX_LATENCY,
				POLLING_INTERVAL);
	}
}
