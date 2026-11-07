package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import net.irisshaders.iris.Iris;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

public final class DepthPrepassTuner {
	public static boolean auto = true;
	public static boolean decided = true;

	private static final int RING = 8;
	private static final int BLOCK = 4;
	private static final int SAMPLES = 16;
	private static final int MAX_EVAL_FRAMES = 400;
	private static final long MIN_INTERVAL_NANOS = 8_000_000_000L;
	private static final long MAX_INTERVAL_NANOS = 64_000_000_000L;
	private static final long MIN_SAVING_NANOS = 50_000L;

	private static int[] begins;
	private static int[] ends;
	private static final boolean[] slotMode = new boolean[RING];
	private static final int[] slotEpoch = new int[RING];
	private static int head;
	private static int tail;
	private static int pending;
	private static int epoch;
	private static boolean evaluating;
	private static boolean measuring;
	private static boolean current;
	private static int evalFrame;
	private static long sumOn;
	private static long sumOff;
	private static int samplesOn;
	private static int samplesOff;
	private static long nextEval;
	private static long interval = MIN_INTERVAL_NANOS;

	private DepthPrepassTuner() {
	}

	public static boolean begin() {
		if (!auto) {
			return true;
		}
		if (begins == null) {
			begins = new int[RING];
			ends = new int[RING];
			GL15.glGenQueries(begins);
			GL15.glGenQueries(ends);
		}
		collect();
		long now = System.nanoTime();
		if (!evaluating && now - nextEval >= 0) {
			evaluating = true;
			epoch++;
			evalFrame = 0;
			sumOn = 0;
			sumOff = 0;
			samplesOn = 0;
			samplesOff = 0;
		}
		if (evaluating) {
			if (samplesOn >= SAMPLES && samplesOff >= SAMPLES) {
				double on = sumOn / (double) samplesOn;
				double off = sumOff / (double) samplesOff;
				boolean use = on < off * 0.97 && off - on > MIN_SAVING_NANOS;
				if (use != decided) {
					Iris.logger.info("Depth prepass " + (use ? "on" : "off") + String.format(" (terrain %.3f ms with, %.3f ms without)", on / 1e6, off / 1e6));
				}
				interval = use == decided && Math.abs(off - on) > off * 0.1 ? Math.min(interval * 2, MAX_INTERVAL_NANOS) : MIN_INTERVAL_NANOS;
				decided = use;
				finish(now);
			} else if (evalFrame >= MAX_EVAL_FRAMES) {
				finish(now);
			}
		}
		current = evaluating ? ((evalFrame++ / BLOCK) & 1) == 0 : decided;
		measuring = evaluating && pending < RING;
		if (measuring) {
			GL33.glQueryCounter(begins[head], GL33.GL_TIMESTAMP);
		}
		return current;
	}

	public static void end() {
		if (!measuring) {
			return;
		}
		measuring = false;
		GL33.glQueryCounter(ends[head], GL33.GL_TIMESTAMP);
		slotMode[head] = current;
		slotEpoch[head] = epoch;
		head = (head + 1) % RING;
		pending++;
	}

	private static void finish(long now) {
		evaluating = false;
		nextEval = now + interval;
	}

	private static void collect() {
		while (pending > 0 && GL15.glGetQueryObjecti(ends[tail], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
			long elapsed = GL33.glGetQueryObjecti64(ends[tail], GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjecti64(begins[tail], GL15.GL_QUERY_RESULT);
			if (evaluating && slotEpoch[tail] == epoch && elapsed > 0) {
				if (slotMode[tail]) {
					sumOn += elapsed;
					samplesOn++;
				} else {
					sumOff += elapsed;
					samplesOff++;
				}
			}
			tail = (tail + 1) % RING;
			pending--;
		}
	}
}
