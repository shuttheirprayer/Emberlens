package net.irisshaders.iris.compat.flywheel;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public final class IrisFlwPrograms {
	private static final Long2ObjectMap<IrisFlwProgram> CACHE = new Long2ObjectOpenHashMap<>();
	private static final Long2ObjectMap<Pending> PENDING = new Long2ObjectOpenHashMap<>();
	private static IrisRenderingPipeline owner;
	private static IrisRenderingPipeline brokenFor;

	private IrisFlwPrograms() {
	}

	public static boolean active() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
		return pipeline instanceof IrisRenderingPipeline && pipeline != brokenFor;
	}

	public static GlProgram get(GlProgram base, boolean shadow) {
		IrisRenderingPipeline pipeline = (IrisRenderingPipeline) Iris.getPipelineManager().getPipelineNullable();
		if (pipeline != owner) {
			CACHE.clear();
			releasePending();
			owner = pipeline;
		}
		long key = ((long) base.handle() << 1) | (shadow ? 1L : 0L);
		IrisFlwProgram program = CACHE.get(key);
		if (program == null) {
			Pending pending = PENDING.get(key);
			if (pending == null) {
				pending = prepare(pipeline, base, shadow);
				if (pending.warm != null) {
					PENDING.put(key, pending);
				}
			}
			if (pending.warm != null && !pending.warm.ready()) {
				return IrisFlwProgram.PENDING;
			}
			PENDING.remove(key);
			program = create(pipeline, shadow, pending);
			if (pending.warm != null) {
				pending.warm.release();
			}
			CACHE.put(key, program);
		}
		if (program == IrisFlwProgram.SKIP && !shadow) {
			brokenFor = pipeline;
			throw new IllegalStateException("Shader pack program for Flywheel could not be built");
		}
		return program;
	}

	private record Pending(String name, Map<PatchShaderType, String> sources, FlwShaderWarmer.Warm warm) {
	}

	private static Pending prepare(IrisRenderingPipeline pipeline, GlProgram base, boolean shadow) {
		String name = (shadow ? "flywheel_shadow_" : "flywheel_") + base.handle();
		String source = FlywheelCompat.vertexSourceOf(base.handle());
		if (source == null) {
			Iris.logger.error("No captured Flywheel vertex source for program {}", base.handle());
			return new Pending(name, null, null);
		}
		try {
			Map<PatchShaderType, String> patched = pipeline.flywheelPatched(name, shadow);
			if (patched == null) {
				return new Pending(name, null, null);
			}
			Supplier<Map<PatchShaderType, String>> build = () -> {
				Map<PatchShaderType, String> sources = new EnumMap<>(patched);
				sources.put(PatchShaderType.VERTEX, FlwVertexSplicer.splice(patched.get(PatchShaderType.VERTEX), source));
				return sources;
			};
			FlwShaderWarmer.Warm warm = FlwShaderWarmer.submit(build);
			return warm == null ? new Pending(name, build.get(), null) : new Pending(name, null, warm);
		} catch (Throwable e) {
			Iris.logger.error("Failed to build shader pack program for Flywheel", e);
			return new Pending(name, null, null);
		}
	}

	private static IrisFlwProgram create(IrisRenderingPipeline pipeline, boolean shadow, Pending pending) {
		Map<PatchShaderType, String> sources = pending.warm != null ? pending.warm.sources() : pending.sources;
		if (sources == null) {
			return IrisFlwProgram.SKIP;
		}
		try {
			ExtendedShader shader = pipeline.createFlywheelShader(pending.name, shadow, sources);
			return shader == null ? IrisFlwProgram.SKIP : new IrisFlwProgram(shader, shadow);
		} catch (Throwable e) {
			Iris.logger.error("Failed to build shader pack program for Flywheel", e);
			return IrisFlwProgram.SKIP;
		}
	}

	public static void clear() {
		if (owner != null && Iris.getPipelineManager().getPipelineNullable() == owner) {
			for (IrisFlwProgram program : CACHE.values()) {
				if (program.shader != null) {
					owner.releaseFlywheelShader(program.shader);
				}
			}
		}
		CACHE.clear();
		releasePending();
	}

	private static void releasePending() {
		for (Pending pending : PENDING.values()) {
			if (pending.warm != null) {
				pending.warm.release();
			}
		}
		PENDING.clear();
	}
}
