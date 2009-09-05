package fiji.pluginManager.logic;

import fiji.pluginManager.logic.PluginObject.Status;

import fiji.pluginManager.util.Progress;
import fiji.pluginManager.util.Util;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: this class is misnomed!
// TODO: this should be merged into PluginCollection

/*
 * PluginListBuilder's overall role is to be in charge of building of a plugin
 * list for interface usage.
 *
 * 1st step: Get information of local plugins (checksums and version)
 * 2nd step: Given XML file, get information of latest Fiji plugins (checksums
 * and version)
 * 3rd step: Build up list of "PluginObject" using both local and updates
 *
 * digests and dates hold checksums and versions of local plugins respectively
 * latestDigests and latestDates hold checksums and versions of latest Fiji
 * plugins
 */
public class PluginListBuilder {
	// TODO: refactor Progress to util.Progress
	protected Progress progress;
	int counter, total;

	public PluginListBuilder(Progress progress) {
		this.progress = progress;
		progress.setTitle("Checksumming");
	}

	static class StringPair {
		String path, realPath;
		StringPair(String path, String realPath) {
			this.path = path;
			this.realPath = realPath;
		}
	}

	protected List<StringPair> queue;

	public void queueDir(String[] dirs, String[] extensions) {
		Set<String> set = new HashSet<String>();
		for (String extension : extensions)
			set.add(extension);
		for (String dir : dirs)
			queueDir(dir, set);
	}

	public void queueDir(String dir, Set<String> extensions) {
		for (String item : new File(Util.prefix(dir)).list()) {
			String path = dir + "/" + item;
			File file = new File(Util.prefix(path));
			if (file.isDirectory()) {
				if (!item.equals(".") && !item.equals(".."))
					queueDir(path, extensions);
				continue;
			}
			int dot = item.lastIndexOf('.');
			if (dot < 0 || !extensions.contains(item.substring(dot)))
				continue;
			queue(path);
		}
	}

	protected void queueIfExists(String path) {
		if (new File(Util.prefix(path)).exists())
			queue(path);
	}

	protected void queue(String path) {
		queue(path, path);
	}

	protected void queue(String path, String realPath) {
		queue.add(new StringPair(path, realPath));
	}

	protected void handle(StringPair pair) {
		String path = pair.path;
		String realPath = Util.prefix(pair.realPath);
		progress.addItem(path);

		String checksum = "INVALID";
		try {
			checksum = Util.getDigest(path, realPath);
		} catch (Exception e) { e.printStackTrace(); }
		long timestamp = Util.getTimestamp(realPath);

		PluginCollection plugins = PluginCollection.getInstance();
		PluginObject plugin = plugins.getPlugin(path);
		if (plugin != null)
			plugin.setLocalVersion(checksum, timestamp);
		else
			plugins.add(new PluginObject(path, checksum,
				timestamp, Status.NOT_FIJI));
		counter += (int)Util.getFilesize(realPath);
		progress.setItemCount(1, 1);
		progress.setCount(counter, total);
	}

	public void updateFromLocal() {
		queue = new ArrayList<StringPair>();

		for (String launcher : Util.isDeveloper ?
					Util.launchers : Util.getLaunchers())
				queueIfExists(launcher);

		queue("ij.jar");

		queueDir(new String[] { "jars", "retro", "misc" },
				new String[] { ".jar", ".class" });
		queueDir(new String[] { "plugins" },
				new String[] { ".jar", ".class",
					".py", ".rb", ".clj", ".js", ".bsh",
					".txt", ".ijm" });
		queueDir(new String[] { "macros" },
				new String[] { ".txt", ".ijm" });
		queueDir(new String[] { "luts" }, new String[] { ".lut" });

		total = 0;
		for (StringPair pair : queue)
			total += Util.getFilesize(pair.realPath);
		counter = 0;
		for (StringPair pair : queue)
			handle(pair);
	}
}
