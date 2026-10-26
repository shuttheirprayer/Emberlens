package net.irisshaders.iris.compat.flywheel;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraftforge.fml.loading.LoadingModList;

public final class FlywheelCompat {
	public static final boolean LOADED = LoadingModList.get().getModFileById("flywheel") != null;

	private static final Int2ObjectMap<String> VERTEX_SOURCES = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<String> PROGRAM_VERTEX_SOURCES = new Int2ObjectOpenHashMap<>();

	private FlywheelCompat() {
	}

	public static void onVertexShaderCompiled(int shader, String source) {
		VERTEX_SOURCES.put(shader, source);
	}

	public static void onProgramLinked(int program, int[] shaders) {
		for (int shader : shaders) {
			String source = VERTEX_SOURCES.get(shader);
			if (source != null) {
				PROGRAM_VERTEX_SOURCES.put(program, source);
				return;
			}
		}
		PROGRAM_VERTEX_SOURCES.remove(program);
	}

	public static String vertexSourceOf(int program) {
		return PROGRAM_VERTEX_SOURCES.get(program);
	}
}
