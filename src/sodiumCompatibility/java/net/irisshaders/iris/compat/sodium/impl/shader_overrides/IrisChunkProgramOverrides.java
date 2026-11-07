package net.irisshaders.iris.compat.sodium.impl.shader_overrides;

import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.impl.IrisChunkShaderBindingPoints;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.SodiumTerrainPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL43C;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IrisChunkProgramOverrides {
	private static final Pattern ALPHA_DISCARD = Pattern.compile("\\.a\\s*<=?\\s*([0-9]*\\.?[0-9]+)[^;{}]{0,24}\\{?\\s*discard");
	public static boolean depthPrepass = true;
	public static boolean prepassActive;
	public static boolean prepassReady;
	public static boolean prepassSolidNoDepthWrite = true;
	public static boolean prepassCutoutNoDepthWrite = true;
	public static float prepassAlphaFloor = 0.0f;
	public static float prepassAlphaMargin = 0.05f;
	public static boolean prepassDone;
	public static boolean prepassSkipBands = true;
	private static final Pattern VERSION = Pattern.compile("#version\\s+(\\d+)[^\\n]*");
	private final EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>> programs = new EnumMap<>(IrisTerrainPass.class);
	private final EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>> prepassPrograms = new EnumMap<>(IrisTerrainPass.class);
	private boolean invariantTerrain;
	private boolean shadersCreated = false;
	private int versionCounterForSodiumShaderReload = -1;

	private GlShader createVertexShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		Optional<String> irisVertexShader;

		if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
			irisVertexShader = pipeline.getShadowVertexShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			irisVertexShader = pipeline.getTerrainSolidVertexShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			irisVertexShader = pipeline.getTerrainCutoutVertexShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			irisVertexShader = pipeline.getTranslucentVertexShaderSource();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}

		String source = irisVertexShader.orElse(null);

		if (source == null) {
			return null;
		}

		if (invariantTerrain && (pass == IrisTerrainPass.GBUFFER_SOLID || pass == IrisTerrainPass.GBUFFER_CUTOUT)) {
			source = withInvariantPosition(source);
		}

		return new GlShader(ShaderType.VERTEX, new ResourceLocation("iris",
			"sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".vsh"), source);
	}

	private GlShader createGeometryShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		Optional<String> irisGeometryShader;

		if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
			irisGeometryShader = pipeline.getShadowGeometryShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			irisGeometryShader = pipeline.getTerrainSolidGeometryShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			irisGeometryShader = pipeline.getTerrainCutoutGeometryShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			irisGeometryShader = pipeline.getTranslucentGeometryShaderSource();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}

		String source = irisGeometryShader.orElse(null);

		if (source == null) {
			return null;
		}

		return new GlShader(IrisShaderTypes.GEOMETRY, new ResourceLocation("iris",
			"sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".gsh"), source);
	}

	private GlShader createTessControlShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		Optional<String> irisTessControlShader;

		if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
			irisTessControlShader = pipeline.getShadowTessControlShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			irisTessControlShader = pipeline.getTerrainSolidTessControlShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			irisTessControlShader = pipeline.getTerrainCutoutTessControlShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			irisTessControlShader = pipeline.getTranslucentTessControlShaderSource();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}

		String source = irisTessControlShader.orElse(null);

		if (source == null) {
			return null;
		}

		return new GlShader(IrisShaderTypes.TESS_CONTROL, new ResourceLocation("iris",
			"sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".tcs"), source);
	}

	private GlShader createTessEvalShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		Optional<String> irisTessEvalShader;

		if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
			irisTessEvalShader = pipeline.getShadowTessEvalShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			irisTessEvalShader = pipeline.getTerrainSolidTessEvalShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			irisTessEvalShader = pipeline.getTerrainCutoutTessEvalShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			irisTessEvalShader = pipeline.getTranslucentTessEvalShaderSource();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}

		String source = irisTessEvalShader.orElse(null);

		if (source == null) {
			return null;
		}

		return new GlShader(IrisShaderTypes.TESS_EVAL, new ResourceLocation("iris",
			"sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".tes"), source);
	}

	private GlShader createFragmentShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		Optional<String> irisFragmentShader;

		if (pass == IrisTerrainPass.SHADOW) {
			irisFragmentShader = pipeline.getShadowFragmentShaderSource();
		} else if (pass == IrisTerrainPass.SHADOW_CUTOUT) {
			irisFragmentShader = pipeline.getShadowCutoutFragmentShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			irisFragmentShader = pipeline.getTerrainSolidFragmentShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			irisFragmentShader = pipeline.getTerrainCutoutFragmentShaderSource();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			irisFragmentShader = pipeline.getTranslucentFragmentShaderSource();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}

		String source = irisFragmentShader.orElse(null);

		if (source == null) {
			return null;
		}

		return new GlShader(ShaderType.FRAGMENT, new ResourceLocation("iris",
			"sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".fsh"), source);
	}

	private static String withInvariantPosition(String source) {
		if (source.contains("invariant gl_Position")) {
			return source;
		}
		int init = source.indexOf("void _vert_init()");
		return source.substring(0, init) + "invariant gl_Position;\n" + source.substring(init);
	}

	private static String terrainVertexSource(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		return (pass == IrisTerrainPass.GBUFFER_SOLID ? pipeline.getTerrainSolidVertexShaderSource() : pipeline.getTerrainCutoutVertexShaderSource()).orElse(null);
	}

	private static String terrainFragmentSource(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		return (pass == IrisTerrainPass.GBUFFER_SOLID ? pipeline.getTerrainSolidFragmentShaderSource() : pipeline.getTerrainCutoutFragmentShaderSource()).orElse(null);
	}

	private static boolean canPrepass(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		boolean solid = pass == IrisTerrainPass.GBUFFER_SOLID;
		if ((solid ? pipeline.getTerrainSolidGeometryShaderSource() : pipeline.getTerrainCutoutGeometryShaderSource()).isPresent()
			|| (solid ? pipeline.getTerrainSolidTessControlShaderSource() : pipeline.getTerrainCutoutTessControlShaderSource()).isPresent()
			|| (solid ? pipeline.getTerrainSolidTessEvalShaderSource() : pipeline.getTerrainCutoutTessEvalShaderSource()).isPresent()) {
			return false;
		}
		String vertex = terrainVertexSource(pass, pipeline);
		String fragment = terrainFragmentSource(pass, pipeline);
		return vertex != null && fragment != null && vertex.contains("void _vert_init()") && vertex.contains("_vert_init();")
			&& !fragment.contains("gl_FragDepth");
	}

	@Nullable
	private GlProgram<IrisChunkShaderInterface> createPrepassShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline, ChunkVertexType vertexType) {
		String source = withInvariantPosition(terrainVertexSource(pass, pipeline));
		Matcher matcher = VERSION.matcher(source);
		boolean found = matcher.find();
		String versionLine = found ? matcher.group() : "#version 330 core";
		boolean modern = !found || Integer.parseInt(matcher.group(1)) >= 130;
		int init = source.indexOf("void _vert_init()");
		source = source.substring(0, init) + (modern ? "out" : "varying") + " vec2 ember_prepassUv;\n" + source.substring(init);
		source = source.replace("_vert_init();", "_vert_init(); ember_prepassUv = _vert_tex_diffuse_coord;");
		float reference = getAlphaReference(pass, pipeline);
		float threshold = Math.max(prepassAlphaFloor, Math.min(Math.max(reference, packDiscardAlpha(terrainFragmentSource(pass, pipeline))) + prepassAlphaMargin, 0.99f));
		Iris.logger.info("Depth prepass alpha threshold for " + pass + ": " + threshold);
		String fragment = versionLine + "\n"
			+ "uniform sampler2D gtexture;\n"
			+ (modern ? "in" : "varying") + " vec2 ember_prepassUv;\n"
			+ "void main() {\n"
			+ "    if (" + (modern ? "texture" : "texture2D") + "(gtexture, ember_prepassUv).a < " + threshold + ") discard;\n"
			+ "}\n";
		String name = "sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + "-prepass";
		GlShader vertShader = new GlShader(ShaderType.VERTEX, new ResourceLocation("iris", name + ".vsh"), source);
		GlShader fragShader = new GlShader(ShaderType.FRAGMENT, new ResourceLocation("iris", name + ".fsh"), fragment);
		try {
			return GlProgram.builder(new ResourceLocation("sodium", "chunk_shader_for_" + pass.getName() + "_prepass"))
				.attachShader(vertShader)
				.attachShader(fragShader)
				.bindAttribute("a_PosId", IrisChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID)
				.bindAttribute("a_Color", IrisChunkShaderBindingPoints.ATTRIBUTE_COLOR)
				.bindAttribute("a_TexCoord", IrisChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE)
				.bindAttribute("a_LightCoord", IrisChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE)
				.bindAttribute("mc_Entity", IrisChunkShaderBindingPoints.BLOCK_ID)
				.bindAttribute("mc_midTexCoord", IrisChunkShaderBindingPoints.MID_TEX_COORD)
				.bindAttribute("at_tangent", IrisChunkShaderBindingPoints.TANGENT)
				.bindAttribute("iris_Normal", IrisChunkShaderBindingPoints.NORMAL)
				.bindAttribute("at_midBlock", IrisChunkShaderBindingPoints.MID_BLOCK)
				.link((shader) -> {
					int handle = ((GlObject) shader).handle();
					GLDebug.nameObject(GL43C.GL_PROGRAM, handle, name);
					return new IrisChunkShaderInterface(handle, (ShaderBindingContextExt) shader, pipeline, new ChunkShaderOptions(ChunkFogMode.SMOOTH, pass.toTerrainPass(), vertexType),
						false, false, getBlendOverride(pass, pipeline), getBufferBlendOverride(pass, pipeline), reference, pipeline.getCustomUniforms());
				});
		} catch (RuntimeException e) {
			Iris.logger.warn("Depth prepass disabled for " + pass + ": " + e.getMessage());
			return null;
		} finally {
			vertShader.delete();
			fragShader.delete();
		}
	}

	private static float packDiscardAlpha(String fragment) {
		float highest = 0.0f;
		Matcher matcher = ALPHA_DISCARD.matcher(fragment);
		while (matcher.find()) {
			highest = Math.max(highest, Float.parseFloat(matcher.group(1)));
		}
		return highest;
	}

	private void createPrepassShaders(SodiumTerrainPipeline pipeline, ChunkVertexType vertexType) {
		GlProgram<IrisChunkShaderInterface> solid = createPrepassShader(IrisTerrainPass.GBUFFER_SOLID, pipeline, vertexType);
		GlProgram<IrisChunkShaderInterface> cutout = solid == null ? null : createPrepassShader(IrisTerrainPass.GBUFFER_CUTOUT, pipeline, vertexType);
		if (solid == null || cutout == null) {
			if (solid != null) {
				solid.delete();
			}
			return;
		}
		prepassPrograms.put(IrisTerrainPass.GBUFFER_SOLID, solid);
		prepassPrograms.put(IrisTerrainPass.GBUFFER_CUTOUT, cutout);
		prepassReady = true;
	}

	private void deletePrepassShaders() {
		for (GlProgram<?> program : this.prepassPrograms.values()) {
			program.delete();
		}
		this.prepassPrograms.clear();
		prepassReady = false;
	}

	private BlendModeOverride getBlendOverride(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
			return pipeline.getShadowBlendOverride();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			return pipeline.getTerrainSolidBlendOverride();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			return pipeline.getTerrainCutoutBlendOverride();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			return pipeline.getTranslucentBlendOverride();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}
	}

	private List<BufferBlendOverride> getBufferBlendOverride(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
			return pipeline.getShadowBufferOverrides();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			return pipeline.getTerrainSolidBufferOverrides();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			return pipeline.getTerrainCutoutBufferOverrides();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			return pipeline.getTranslucentBufferOverrides();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}
	}

	@Nullable
	private GlProgram<IrisChunkShaderInterface> createShader(IrisTerrainPass pass, SodiumTerrainPipeline pipeline, ChunkVertexType vertexType) {
		GlShader vertShader = createVertexShader(pass, pipeline);
		GlShader geomShader = createGeometryShader(pass, pipeline);
		GlShader tessCShader = createTessControlShader(pass, pipeline);
		GlShader tessEShader = createTessEvalShader(pass, pipeline);
		GlShader fragShader = createFragmentShader(pass, pipeline);
		BlendModeOverride blendOverride = getBlendOverride(pass, pipeline);
		List<BufferBlendOverride> bufferOverrides = getBufferBlendOverride(pass, pipeline);
		float alpha = getAlphaReference(pass, pipeline);

		if (vertShader == null || fragShader == null) {
			if (vertShader != null) {
				vertShader.delete();
			}

			if (geomShader != null) {
				geomShader.delete();
			}

			if (tessCShader != null) {
				tessCShader.delete();
			}

			if (tessEShader != null) {
				tessEShader.delete();
			}

			if (fragShader != null) {
				fragShader.delete();
			}

			// TODO: Partial shader programs?
			return null;
		}

		try {
			GlProgram.Builder builder = GlProgram.builder(new ResourceLocation("sodium", "chunk_shader_for_"
				+ pass.getName()));

			if (geomShader != null) {
				builder.attachShader(geomShader);
			}
			if (tessCShader != null) {
				builder.attachShader(tessCShader);
			}
			if (tessEShader != null) {
				builder.attachShader(tessEShader);
			}

			return builder.attachShader(vertShader)
				.attachShader(fragShader)
				// The following 4 attributes are part of Sodium.
				.bindAttribute("a_PosId", IrisChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID)
				.bindAttribute("a_Color", IrisChunkShaderBindingPoints.ATTRIBUTE_COLOR)
				.bindAttribute("a_TexCoord", IrisChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE)
				.bindAttribute("a_LightCoord", IrisChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE)
				.bindAttribute("mc_Entity", IrisChunkShaderBindingPoints.BLOCK_ID)
				.bindAttribute("mc_midTexCoord", IrisChunkShaderBindingPoints.MID_TEX_COORD)
				.bindAttribute("at_tangent", IrisChunkShaderBindingPoints.TANGENT)
				.bindAttribute("iris_Normal", IrisChunkShaderBindingPoints.NORMAL)
				.bindAttribute("at_midBlock", IrisChunkShaderBindingPoints.MID_BLOCK)
				.link((shader) -> {
					int handle = ((GlObject) shader).handle();
					ShaderBindingContextExt contextExt = (ShaderBindingContextExt) shader;
					GLDebug.nameObject(GL43C.GL_PROGRAM, handle, "sodium-terrain-" + pass.toString().toLowerCase(Locale.ROOT));
					return new IrisChunkShaderInterface(handle, contextExt, pipeline, new ChunkShaderOptions(ChunkFogMode.SMOOTH, pass.toTerrainPass(), vertexType),
						tessCShader != null || tessEShader != null, pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT, blendOverride, bufferOverrides, alpha, pipeline.getCustomUniforms());
				});
		} finally {
			vertShader.delete();
			if (geomShader != null) {
				geomShader.delete();
			}
			if (tessCShader != null) {
				tessCShader.delete();
			}
			if (tessEShader != null) {
				tessEShader.delete();
			}
			fragShader.delete();
		}
	}

	private float getAlphaReference(IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
		if (pass == IrisTerrainPass.SHADOW || pass == IrisTerrainPass.SHADOW_CUTOUT) {
			return pipeline.getShadowAlpha().orElse(AlphaTests.ONE_TENTH_ALPHA).reference();
		} else if (pass == IrisTerrainPass.GBUFFER_SOLID) {
			return AlphaTest.ALWAYS.reference();
		} else if (pass == IrisTerrainPass.GBUFFER_CUTOUT) {
			return pipeline.getTerrainCutoutAlpha().orElse(AlphaTests.ONE_TENTH_ALPHA).reference();
		} else if (pass == IrisTerrainPass.GBUFFER_TRANSLUCENT) {
			return pipeline.getTranslucentAlpha().orElse(AlphaTest.ALWAYS).reference();
		} else {
			throw new IllegalArgumentException("Unknown pass type " + pass);
		}
	}

	private SodiumTerrainPipeline getSodiumTerrainPipeline() {
		WorldRenderingPipeline worldRenderingPipeline = Iris.getPipelineManager().getPipelineNullable();

		if (worldRenderingPipeline != null) {
			return worldRenderingPipeline.getSodiumTerrainPipeline();
		} else {
			return null;
		}
	}

	public void createShaders(SodiumTerrainPipeline pipeline, ChunkVertexType vertexType) {
		deletePrepassShaders();
		if (pipeline != null) {
			pipeline.patchShaders(vertexType);
			invariantTerrain = canPrepass(IrisTerrainPass.GBUFFER_SOLID, pipeline) && canPrepass(IrisTerrainPass.GBUFFER_CUTOUT, pipeline);
			for (IrisTerrainPass pass : IrisTerrainPass.values()) {
				if (pass.isShadow() && !pipeline.hasShadowPass()) {
					this.programs.put(pass, null);
					continue;
				}

				this.programs.put(pass, createShader(pass, pipeline, vertexType));
			}
			if (invariantTerrain && this.programs.get(IrisTerrainPass.GBUFFER_SOLID) != null && this.programs.get(IrisTerrainPass.GBUFFER_CUTOUT) != null) {
				createPrepassShaders(pipeline, vertexType);
			}
		} else {
			for (GlProgram<?> program : this.programs.values()) {
				if (program != null) {
					program.delete();
				}
			}
			this.programs.clear();
		}

		shadersCreated = true;
	}

	@Nullable
	public GlProgram<IrisChunkShaderInterface> getProgramOverride(TerrainRenderPass pass, ChunkVertexType vertexType) {
		if (versionCounterForSodiumShaderReload != Iris.getPipelineManager().getVersionCounterForSodiumShaderReload()) {
			versionCounterForSodiumShaderReload = Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
			deleteShaders();
		}

		WorldRenderingPipeline worldRenderingPipeline = Iris.getPipelineManager().getPipelineNullable();
		SodiumTerrainPipeline sodiumTerrainPipeline = null;

		if (worldRenderingPipeline != null) {
			sodiumTerrainPipeline = worldRenderingPipeline.getSodiumTerrainPipeline();
		}

		if (!shadersCreated) {
			createShaders(sodiumTerrainPipeline, vertexType);
		}

		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			if (sodiumTerrainPipeline != null && !sodiumTerrainPipeline.hasShadowPass()) {
				throw new IllegalStateException("Shadow program requested, but the pack does not have a shadow pass?");
			}

			if (pass.supportsFragmentDiscard()) {
				return this.programs.get(IrisTerrainPass.SHADOW_CUTOUT);
			} else {
				return this.programs.get(IrisTerrainPass.SHADOW);
			}
		} else {
			if (prepassActive && prepassReady && !pass.isReverseOrder()) {
				return this.prepassPrograms.get(pass.supportsFragmentDiscard() ? IrisTerrainPass.GBUFFER_CUTOUT : IrisTerrainPass.GBUFFER_SOLID);
			}
			if (pass.supportsFragmentDiscard()) {
				return this.programs.get(IrisTerrainPass.GBUFFER_CUTOUT);
			} else if (pass.isReverseOrder()) {
				return this.programs.get(IrisTerrainPass.GBUFFER_TRANSLUCENT);
			} else {
				return this.programs.get(IrisTerrainPass.GBUFFER_SOLID);
			}
		}
	}

	public void bindFramebuffer(TerrainRenderPass pass) {
		SodiumTerrainPipeline pipeline = getSodiumTerrainPipeline();
		boolean isShadowPass = ShadowRenderingState.areShadowsCurrentlyBeingRendered();

		if (pipeline != null) {
			GlFramebuffer framebuffer;

			if (isShadowPass) {
				framebuffer = pipeline.getShadowFramebuffer();
			} else if (pass.isReverseOrder()) {
				framebuffer = pipeline.getTranslucentFramebuffer();
			} else {
				framebuffer = pipeline.getTerrainSolidFramebuffer();
			}

			if (framebuffer != null) {
				framebuffer.bind();
			}
		}
	}

	public void unbindFramebuffer() {
		SodiumTerrainPipeline pipeline = getSodiumTerrainPipeline();

		if (pipeline != null) {
			Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
		}
	}

	public void deleteShaders() {
		deletePrepassShaders();
		for (GlProgram<?> program : this.programs.values()) {
			if (program != null) {
				program.delete();
			}
		}

		this.programs.clear();
		shadersCreated = false;
	}
}
