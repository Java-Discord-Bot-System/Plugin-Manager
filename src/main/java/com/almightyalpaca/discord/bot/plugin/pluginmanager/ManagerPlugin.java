package com.almightyalpaca.discord.bot.plugin.pluginmanager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.almightyalpaca.discord.bot.system.PluginSelector;
import com.almightyalpaca.discord.bot.system.command.AbstractCommand;
import com.almightyalpaca.discord.bot.system.command.annotation.Command;
import com.almightyalpaca.discord.bot.system.command.arguments.special.Rest;
import com.almightyalpaca.discord.bot.system.events.CommandEvent;
import com.almightyalpaca.discord.bot.system.exception.PluginLoadingException;
import com.almightyalpaca.discord.bot.system.exception.PluginUnloadingException;
import com.almightyalpaca.discord.bot.system.extension.ExtensionClassLoader;
import com.almightyalpaca.discord.bot.system.extension.ExtensionManager;
import com.almightyalpaca.discord.bot.system.extension.PluginExtension;
import com.almightyalpaca.discord.bot.system.plugins.Plugin;
import com.almightyalpaca.discord.bot.system.plugins.PluginInfo;
import com.almightyalpaca.discord.bot.system.util.GCUtil;

import net.dv8tion.jda.MessageBuilder;
import net.dv8tion.jda.MessageBuilder.Formatting;

public class ManagerPlugin extends Plugin {

	class PluginsCommand extends AbstractCommand {

		public PluginsCommand() {
			super("plugins", "Manage Plugins", "");
		}

		@Command(dm = true, guild = true, async = true)
		private void onCommand(final CommandEvent event, final String string) {
			if (string.equalsIgnoreCase("list")) {
				final MessageBuilder builder = new MessageBuilder();
				builder.appendString("Plugins:\n", Formatting.BOLD);
				for (final PluginExtension plugin : ManagerPlugin.this.manager.plugins) {
					builder.appendString(plugin.getPluginInfo().getName() + "\n");
				}
				event.sendMessage(builder.build());
			} else {
				this.onCommand(event, string, "");
			}
		}

		@Command(dm = true, guild = true, async = true)
		private void onCommand(final CommandEvent event, final String action, final Rest plugin) {
			this.onCommand(event, action, plugin.getString());
		}

		@Command(dm = true, guild = true, async = true)
		private void onCommand(final CommandEvent event, final String action, final String plugin) {
			switch (action) {
				case "load":
					ManagerPlugin.this.load(plugin);
					break;
				case "unload":
					ManagerPlugin.this.unload(p -> p.getPluginInfo().getName().equalsIgnoreCase(plugin) || p.getPluginInfo().getName().replace(" Plugin", "").equalsIgnoreCase(plugin));
					break;
				case "reload":
					ManagerPlugin.this.reload(p -> p.getPluginInfo().getName().equalsIgnoreCase(plugin) || p.getPluginInfo().getName().replace(" Plugin", "").equalsIgnoreCase(plugin));
					break;
				case "reloadall":
					ManagerPlugin.this.reload(p -> p.getPluginObject() != ManagerPlugin.this);
					break;
				default:
					break;
			}
		}
	}

	private static final PluginInfo		INFO		= new PluginInfo("com.almightyalpaca.discord.bot.plugin.pluginmanager", "1.0.0", "Almighty Alpaca", "Manager Plugin", "Manage Plugins");

	private ScheduledExecutorService	executor;

	private ExtensionManager			manager;

	private final File					defaultDir	= new File("plugins");

	public ManagerPlugin() {
		super(ManagerPlugin.INFO);
	}

	@Override
	public void load() throws PluginLoadingException {

		this.executor = new ScheduledThreadPoolExecutor(1, (ThreadFactory) r -> {
			final Thread thread = new Thread(r, "PluginReloader-Thread");
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.setDaemon(true);
			return thread;
		});

		try {
			final Field field = this.getBridge().getClass().getDeclaredField("pluginExtension");
			field.setAccessible(true);
			this.manager = ((PluginExtension) field.get(this.getBridge())).getPluginManager();

		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}

		this.registerCommand(new PluginsCommand());
	}

	private void load(final String name) {
		ManagerPlugin.this.manager.loadPlugin(new File(ManagerPlugin.this.defaultDir, name));
	}

	private void reload(final PluginSelector selector) {
		final List<Pair<File, WeakReference<ExtensionClassLoader>>> plugins = new ArrayList<>();
		ManagerPlugin.this.manager.unloadPlugins(p -> {
			if (selector.match(p)) {
				plugins.add(new ImmutablePair<File, WeakReference<ExtensionClassLoader>>(p.getFolder(), new WeakReference<ExtensionClassLoader>(p.getClassLoader())));
				return true;
			} else {
				return false;
			}
		});
		while (!plugins.isEmpty()) {
			final Iterator<Pair<File, WeakReference<ExtensionClassLoader>>> iterator = plugins.iterator();
			while (iterator.hasNext()) {
				final Pair<File, WeakReference<ExtensionClassLoader>> pair = iterator.next();
				if (pair.getValue().get() == null) {
					iterator.remove();
					this.executor.schedule(() -> ManagerPlugin.this.manager.loadPlugin(pair.getKey()), 1, TimeUnit.SECONDS);
				}
			}
			GCUtil.runGC(1);
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void unload() throws PluginUnloadingException {
		this.executor.shutdownNow();
	}

	private void unload(final PluginSelector selector) {
		ManagerPlugin.this.manager.unloadPlugins(selector);
	}
}
