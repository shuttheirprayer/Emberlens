package net.irisshaders.iris.compat.flywheel;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;

public final class IrisFlwPrograms {
	private static final Long2ObjectMap<IrisFlwProgram> CACHE = new Long2ObjectOpenHashMap<>();
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
			owner = pipeline;
		}
		long key = ((long) base.handle() << 1) | (shadow ? 1L : 0L);
		IrisFlwProgram program = CACHE.get(key);
		if (program == null) {
			program = create(pipeline, base, shadow);
			CACHE.put(key, program);
		}
		if (program == IrisFlwProgram.SKIP && !shadow) {
			brokenFor = pipeline;
			throw new IllegalStateException("Shader pack program for Flywheel could not be built");
		}
		return program;
	}

	private static IrisFlwProgram create(IrisRenderingPipeline pipeline, GlProgram base, boolean shadow) {
		String source = FlywheelCompat.vertexSourceOf(base.handle());
		if (source == null) {
			Iris.logger.error("No captured Flywheel vertex source for program {}", base.handle());
			return IrisFlwProgram.SKIP;
		}
		try {
			ExtendedShader shader = pipeline.createFlywheelShader((shadow ? "flywheel_shadow_" : "flywheel_") + base.handle(), shadow,
				vertex -> FlwVertexSplicer.splice(vertex, source));
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
	}
}
