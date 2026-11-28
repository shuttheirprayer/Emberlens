package net.irisshaders.iris.pipeline;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.irisshaders.iris.compat.flywheel.FlwShaderWarmer;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL20C;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class PipelineWarmer {
	private static final Set<String> IRIS_ATTRIBUTES = Set.of("Position", "Color", "Normal", "UV0", "UV1", "UV2");
	private static final IntArrayList PROGRAMS = new IntArrayList();
	private static final IntArrayList SHADERS = new IntArrayList();
	private static final Set<Map<PatchShaderType, String>> SEEN = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final List<Map<PatchShaderType, String>> CAPTURED = new ArrayList<>();
	private static final List<VertexFormat> FORMATS = new ArrayList<>();
	private static final Path KNOWN_FILE = Path.of(".emberlens", "warm.txt");
	private static Set<String> known;
	private static boolean capturing;
	private static boolean cold;
	private static Boolean amd;

	private PipelineWarmer() {
	}

	public static boolean capturing() {
		return capturing;
	}

	static void warm(Function<ShaderKey, ShaderInstance> shaders) {
		cold = false;
		if (!enabled()) {
			return;
		}
		capturing = true;
		try {
			for (ShaderKey key : ShaderKey.values()) {
				shaders.apply(key);
			}
			cold = remember(fingerprint());
			if (cold) {
				for (int i = 0; i < CAPTURED.size(); i++) {
					issue(CAPTURED.get(i), FORMATS.get(i));
				}
			}
		} catch (RuntimeException ignored) {
		} finally {
			capturing = false;
			SEEN.clear();
			CAPTURED.clear();
			FORMATS.clear();
		}
	}

	public static void vanilla(Map<PatchShaderType, String> sources, VertexFormat format) {
		if (SEEN.add(sources)) {
			CAPTURED.add(sources);
			FORMATS.add(format);
		}
	}

	private static void issue(Map<PatchShaderType, String> sources, VertexFormat format) {
		int program = GL20C.glCreateProgram();
		attach(program, GL20C.GL_VERTEX_SHADER, vanillaSource(sources.get(PatchShaderType.VERTEX)));
		attach(program, GL20C.GL_FRAGMENT_SHADER, vanillaSource(sources.get(PatchShaderType.FRAGMENT)));
		attach(program, ShaderType.GEOMETRY.id, vanillaSource(sources.get(PatchShaderType.GEOMETRY)));
		attach(program, ShaderType.TESSELATION_CONTROL.id, vanillaSource(sources.get(PatchShaderType.TESS_CONTROL)));
		attach(program, ShaderType.TESSELATION_EVAL.id, vanillaSource(sources.get(PatchShaderType.TESS_EVAL)));
		List<String> attributes = format.getElementAttributeNames();
		for (int i = 0; i < attributes.size(); i++) {
			String name = attributes.get(i);
			GL20C.glBindAttribLocation(program, i, IRIS_ATTRIBUTES.contains(name) ? "iris_" + name : name);
		}
		link(program);
	}

	static void composite(Map<PatchShaderType, String> sources) {
		if (!cold) {
			return;
		}
		int program = GL20C.glCreateProgram();
		GL20C.glBindAttribLocation(program, 11, "iris_Entity");
		GL20C.glBindAttribLocation(program, 11, "mc_Entity");
		GL20C.glBindAttribLocation(program, 12, "mc_midTexCoord");
		GL20C.glBindAttribLocation(program, 13, "at_tangent");
		GL20C.glBindAttribLocation(program, 14, "at_midBlock");
		GL20C.glBindAttribLocation(program, 0, "Position");
		GL20C.glBindAttribLocation(program, 1, "UV0");
		attach(program, GL20C.GL_VERTEX_SHADER, sources.get(PatchShaderType.VERTEX));
		attach(program, ShaderType.GEOMETRY.id, sources.get(PatchShaderType.GEOMETRY));
		attach(program, GL20C.GL_FRAGMENT_SHADER, sources.get(PatchShaderType.FRAGMENT));
		link(program);
	}

	public static void chunk(String vertex, String geometry, String tessControl, String tessEval, String fragment, String[] attributes, int[] locations) {
		if (vertex == null || fragment == null || !cold) {
			return;
		}
		int program = GL20C.glCreateProgram();
		attach(program, ShaderType.GEOMETRY.id, geometry);
		attach(program, ShaderType.TESSELATION_CONTROL.id, tessControl);
		attach(program, ShaderType.TESSELATION_EVAL.id, tessEval);
		attach(program, GL20C.GL_VERTEX_SHADER, vertex);
		attach(program, GL20C.GL_FRAGMENT_SHADER, fragment);
		for (int i = 0; i < attributes.length; i++) {
			GL20C.glBindAttribLocation(program, locations[i], attributes[i]);
		}
		link(program);
	}

	public static void release() {
		for (int i = 0; i < PROGRAMS.size(); i++) {
			GL20C.glDeleteProgram(PROGRAMS.getInt(i));
		}
		for (int i = 0; i < SHADERS.size(); i++) {
			GL20C.glDeleteShader(SHADERS.getInt(i));
		}
		PROGRAMS.clear();
		SHADERS.clear();
	}

	private static String fingerprint() {
		long hash = 1125899906842597L;
		for (Map<PatchShaderType, String> sources : CAPTURED) {
			for (PatchShaderType type : PatchShaderType.values()) {
				String source = sources.get(type);
				hash = 31 * hash + (source == null ? 0 : source.hashCode());
			}
		}
		return Long.toHexString(hash) + ":" + CAPTURED.size();
	}

	private static boolean remember(String fingerprint) {
		if (known == null) {
			known = new HashSet<>();
			try {
				if (Files.isRegularFile(KNOWN_FILE)) {
					known.addAll(Files.readAllLines(KNOWN_FILE));
				}
			} catch (IOException ignored) {
			}
		}
		if (!known.add(fingerprint)) {
			return false;
		}
		try {
			Files.createDirectories(KNOWN_FILE.getParent());
			Files.writeString(KNOWN_FILE, fingerprint + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
		}
		return true;
	}

	private static boolean enabled() {
		if (amd == null) {
			String vendor = String.valueOf(GL20C.glGetString(GL20C.GL_VENDOR));
			amd = vendor.contains("ATI") || vendor.contains("AMD");
		}
		return amd && FlwShaderWarmer.parallel();
	}

	private static void attach(int program, int type, String source) {
		if (source == null) {
			return;
		}
		int shader = GL20C.glCreateShader(type);
		GL20C.glShaderSource(shader, source);
		GL20C.glCompileShader(shader);
		GL20C.glAttachShader(program, shader);
		SHADERS.add(shader);
	}

	private static void link(int program) {
		GL20C.glLinkProgram(program);
		PROGRAMS.add(program);
	}

	private static String vanillaSource(String source) {
		if (source == null || !source.contains("moj_import")) {
			return source;
		}
		return String.join("", new GlslPreprocessor() {
			@Override
			public String applyImport(boolean local, String path) {
				return null;
			}
		}.process(source));
	}
}
