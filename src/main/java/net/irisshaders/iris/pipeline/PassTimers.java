package net.irisshaders.iris.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PassTimers {
	private static final Logger LOGGER = LogManager.getLogger(PassTimers.class);
	private static final int RING = 8;
	private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();
	public static boolean enabled = false;
	private static boolean open;
	private static int frames;

	private PassTimers() {
	}

	public static void begin(String label) {
		if (!enabled || open) {
			return;
		}
		Slot slot = SLOTS.computeIfAbsent(label, k -> new Slot());
		slot.collect();
		GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, slot.queries[slot.head]);
		slot.head = (slot.head + 1) % RING;
		if (slot.pending < RING) {
			slot.pending++;
		}
		open = true;
	}

	public static void end() {
		if (open) {
			GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
			open = false;
		}
	}

	public static void frame() {
		if (!enabled || ++frames < 2000) {
			return;
		}
		StringBuilder line = new StringBuilder("[passes]");
		for (Map.Entry<String, Slot> entry : SLOTS.entrySet()) {
			Slot slot = entry.getValue();
			if (slot.count > 0) {
				line.append(' ').append(entry.getKey()).append('=').append(slot.total / slot.count / 1000L);
				slot.total = 0;
				slot.count = 0;
			}
		}
		LOGGER.info(line);
		frames = 0;
	}

	private static final class Slot {
		final int[] queries = new int[RING];
		int head;
		int pending;
		long total;
		long count;

		Slot() {
			GL15C.glGenQueries(queries);
		}

		void collect() {
			int query = queries[head];
			if (pending == RING && GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) != 0) {
				total += GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
				count++;
			}
		}
	}
}
