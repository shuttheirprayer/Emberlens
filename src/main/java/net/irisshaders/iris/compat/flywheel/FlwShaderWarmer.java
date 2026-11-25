package net.irisshaders.iris.compat.flywheel;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import org.lwjgl.opengl.ARBParallelShaderCompile;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.KHRParallelShaderCompile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class FlwShaderWarmer {
	private static final long SETTLE_NANOS = 1_500_000_000L;
	private static final Set<String> IRIS_ATTRIBUTES = Set.of("Position", "Color", "Normal", "UV0", "UV1", "UV2");
	private static final Executor SPLICER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Emberlens Shader Splice");
		thread.setDaemon(true);
		thread.setPriority(Thread.NORM_PRIORITY - 1);
		return thread;
	});
	private static Boolean parallel;

	private FlwShaderWarmer() {
	}

	public static final class Warm {
		private final CompletableFuture<Map<PatchShaderType, String>> sources;
		private int vertex;
		private int fragment;
		private int program;
		private long issued;
		private boolean ready;

		Warm(CompletableFuture<Map<PatchShaderType, String>> sources) {
			this.sources = sources;
		}

		public boolean ready() {
			if (ready) {
				return true;
			}
			if (issued == 0L) {
				if (!sources.isDone()) {
					return false;
				}
				Map<PatchShaderType, String> built = sources();
				if (built == null || built.get(PatchShaderType.GEOMETRY) != null || built.get(PatchShaderType.TESS_CONTROL) != null
					|| built.get(PatchShaderType.TESS_EVAL) != null) {
					ready = true;
					return true;
				}
				issue(built);
				issued = System.nanoTime();
				return false;
			}
			ready = System.nanoTime() - issued >= SETTLE_NANOS;
			return ready;
		}

		public Map<PatchShaderType, String> sources() {
			try {
				return sources.getNow(null);
			} catch (RuntimeException e) {
				Iris.logger.error("Failed to build shader pack program for Flywheel", e);
				return null;
			}
		}

		public void release() {
			if (program != 0) {
				GL20C.glDeleteProgram(program);
				GL20C.glDeleteShader(vertex);
				GL20C.glDeleteShader(fragment);
				program = 0;
			}
		}

		private void issue(Map<PatchShaderType, String> built) {
			vertex = shader(GL20C.GL_VERTEX_SHADER, built.get(PatchShaderType.VERTEX));
			fragment = shader(GL20C.GL_FRAGMENT_SHADER, built.get(PatchShaderType.FRAGMENT));
			program = GL20C.glCreateProgram();
			GL20C.glAttachShader(program, vertex);
			GL20C.glAttachShader(program, fragment);
			List<String> attributes = IrisVertexFormats.ENTITY.getElementAttributeNames();
			for (int i = 0; i < attributes.size(); i++) {
				String name = attributes.get(i);
				GL20C.glBindAttribLocation(program, i, IRIS_ATTRIBUTES.contains(name) ? "iris_" + name : name);
			}
			GL20C.glLinkProgram(program);
		}
	}

	public static void prepare(IrisRenderingPipeline pipeline) {
		try {
			pipeline.flywheelPatched("flywheel", false);
			pipeline.flywheelPatched("flywheel_shadow", true);
		} catch (Throwable e) {
			Iris.logger.warn("Could not prepare shader pack programs for Flywheel", e);
		}
	}

	public static Warm submit(Supplier<Map<PatchShaderType, String>> build) {
		if (!parallel()) {
			return null;
		}
		return new Warm(CompletableFuture.supplyAsync(build, SPLICER));
	}

	private static boolean parallel() {
		if (parallel == null) {
			RenderSystem.assertOnRenderThread();
			GLCapabilities caps = GL.getCapabilities();
			if (caps.GL_ARB_parallel_shader_compile) {
				ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(0xFFFFFFFF);
				parallel = true;
			} else if (caps.GL_KHR_parallel_shader_compile) {
				KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(0xFFFFFFFF);
				parallel = true;
			} else {
				parallel = false;
			}
		}
		return parallel;
	}

	private static int shader(int type, String source) {
		String text = source.contains("moj_import") ? String.join("", new GlslPreprocessor() {
			@Override
			public String applyImport(boolean local, String path) {
				return null;
			}
		}.process(source)) : source;
		int shader = GL20C.glCreateShader(type);
		GL20C.glShaderSource(shader, text);
		GL20C.glCompileShader(shader);
		return shader;
	}
}
