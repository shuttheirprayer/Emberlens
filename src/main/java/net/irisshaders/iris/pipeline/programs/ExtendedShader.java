package net.irisshaders.iris.pipeline.programs;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.program.IrisProgramTypes;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gl.uniform.DynamicLocationalUniformHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.ARBTextureSwizzle;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.KHRDebug;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ExtendedShader extends ShaderInstance implements ShaderInstanceInterface {
	private static final Matrix4f identity;
	private static ExtendedShader lastApplied;

	static {
		identity = new Matrix4f();
		identity.identity();
	}

	private final boolean intensitySwizzle;
	private final List<BufferBlendOverride> bufferBlendOverrides;
	private final boolean hasOverrides;
	private final Uniform modelViewInverse;
	private final Uniform projectionInverse;
	private final Uniform normalMatrix;
	private final CustomUniforms customUniforms;
	private final IrisRenderingPipeline parent;
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;
	private final GlFramebuffer writingToBeforeTranslucent;
	private final GlFramebuffer writingToAfterTranslucent;
	private final BlendModeOverride blendModeOverride;
	float alphaTest;
	boolean usesTessellation;
	private static final int[] INTENSITY_SWIZZLE = new int[]{GL30C.GL_RED, GL30C.GL_RED, GL30C.GL_RED, GL30C.GL_RED};
	private static final Matrix4f scratchMatrix4f = new Matrix4f();
	private static final Matrix3f scratchMatrix3f = new Matrix3f();
	private static final Matrix4f cachedProjection = new Matrix4f();
	private static final float[] cachedProjectionInverse = new float[16];
	private static final Matrix4f cachedModelView = new Matrix4f();
	private static final float[] cachedModelViewInverse = new float[16];
	private static final float[] cachedNormalMatrix = new float[9];
	private static boolean hasCachedProjection;
	private static boolean hasCachedModelView;
	private Program geometry, tessControl, tessEval;

	public ExtendedShader(ResourceProvider resourceFactory, String string, VertexFormat vertexFormat, boolean usesTessellation,
						  GlFramebuffer writingToBeforeTranslucent, GlFramebuffer writingToAfterTranslucent,
						  BlendModeOverride blendModeOverride, AlphaTest alphaTest,
						  Consumer<DynamicLocationalUniformHolder> uniformCreator, BiConsumer<SamplerHolder, ImageHolder> samplerCreator, boolean isIntensity,
						  IrisRenderingPipeline parent, @Nullable List<BufferBlendOverride> bufferBlendOverrides, CustomUniforms customUniforms) throws IOException {
		super(resourceFactory, string, vertexFormat);

		GLDebug.nameObject(KHRDebug.GL_SHADER, this.getVertexProgram().getId(), string + "_vertex.vsh");
		GLDebug.nameObject(KHRDebug.GL_SHADER, this.getFragmentProgram().getId(), string + "_fragment.fsh");

		int programId = this.getId();

		GLDebug.nameObject(KHRDebug.GL_PROGRAM, programId, string);

		ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(string, programId);
		ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
		uniformCreator.accept(uniformBuilder);
		ProgramImages.Builder builder = ProgramImages.builder(programId);
		samplerCreator.accept(samplerBuilder, builder);
		customUniforms.mapholderToPass(uniformBuilder, this);
		this.usesTessellation = usesTessellation;

		uniforms = uniformBuilder.buildUniforms();
		this.customUniforms = customUniforms;
		samplers = samplerBuilder.build();
		images = builder.build();
		this.writingToBeforeTranslucent = writingToBeforeTranslucent;
		this.writingToAfterTranslucent = writingToAfterTranslucent;
		this.blendModeOverride = blendModeOverride;
		this.bufferBlendOverrides = bufferBlendOverrides;
		this.hasOverrides = bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty();
		this.alphaTest = alphaTest.reference();
		this.parent = parent;

		this.modelViewInverse = this.getUniform("ModelViewMatInverse");
		this.projectionInverse = this.getUniform("ProjMatInverse");
		this.normalMatrix = this.getUniform("NormalMat");

		this.intensitySwizzle = isIntensity;
	}

	public boolean isIntensitySwizzle() {
		return intensitySwizzle;
	}

	@Override
	public void clear() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		lastApplied = null;

		if (this.blendModeOverride != null || hasOverrides) {
			BlendModeOverride.restore();
		}

		Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
	}

	@Override
	public void apply() {
		CapturedRenderingState.INSTANCE.setCurrentAlphaTest(alphaTest);

		if (lastApplied != this) {
			lastApplied = this;
			ProgramManager.glUseProgram(this.getId());
		}

		if (intensitySwizzle) {
			IrisRenderSystem.texParameteriv(RenderSystem.getShaderTexture(0), TextureType.TEXTURE_2D.getGlType(), ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_RGBA,
				INTENSITY_SWIZZLE);
		}

		IrisRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), IrisSamplers.ALBEDO_TEXTURE_UNIT, RenderSystem.getShaderTexture(0));
		IrisRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), IrisSamplers.OVERLAY_TEXTURE_UNIT, RenderSystem.getShaderTexture(1));
		IrisRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), IrisSamplers.LIGHTMAP_TEXTURE_UNIT, RenderSystem.getShaderTexture(2));

		ImmediateState.usingTessellation = usesTessellation;

		if (PROJECTION_MATRIX != null) {
			if (projectionInverse != null) {
				scratchMatrix4f.set(PROJECTION_MATRIX.getFloatBuffer());

				if (!hasCachedProjection || !scratchMatrix4f.equals(cachedProjection)) {
					cachedProjection.set(scratchMatrix4f);
					scratchMatrix4f.invert().get(cachedProjectionInverse);
					hasCachedProjection = true;
				}

				projectionInverse.set(cachedProjectionInverse);
			}
		} else {
			if (projectionInverse != null) {
				projectionInverse.set(identity);
			}
		}

		if (MODEL_VIEW_MATRIX != null && (modelViewInverse != null || normalMatrix != null)) {
			scratchMatrix4f.set(MODEL_VIEW_MATRIX.getFloatBuffer());

			if (!hasCachedModelView || !scratchMatrix4f.equals(cachedModelView)) {
				cachedModelView.set(scratchMatrix4f);
				scratchMatrix4f.invert().get(cachedModelViewInverse);
				scratchMatrix3f.set(cachedModelView).invert().transpose().get(cachedNormalMatrix);
				hasCachedModelView = true;
			}

			if (modelViewInverse != null) {
				modelViewInverse.set(cachedModelViewInverse);
			}

			if (normalMatrix != null) {
				normalMatrix.set(cachedNormalMatrix);
			}
		}

		uploadIfNotNull(projectionInverse);
		uploadIfNotNull(modelViewInverse);
		uploadIfNotNull(normalMatrix);

		List<Uniform> uniformList = super.uniforms;
		for (Uniform uniform : uniformList) {
			uploadIfNotNull(uniform);
		}

		samplers.update();
		uniforms.update();

		customUniforms.push(this);

		images.update();


		if (this.blendModeOverride != null) {
			this.blendModeOverride.apply();
		}

		if (hasOverrides) {
			bufferBlendOverrides.forEach(BufferBlendOverride::apply);
		}

		if (parent.isBeforeTranslucent) {
			writingToBeforeTranslucent.bind();
		} else {
			writingToAfterTranslucent.bind();
		}
	}

	@Nullable
	@Override
	public Uniform getUniform(String name) {
		// Prefix all uniforms with Iris to help avoid conflicts with existing names within the shader.
		return super.getUniform("iris_" + name);
	}

	private void uploadIfNotNull(Uniform uniform) {
		if (uniform != null) {
			uniform.upload();
		}
	}

	@Override
	public void attachToProgram() {
		super.attachToProgram();
		if (this.geometry != null) {
			this.geometry.attachToShader(this);
		}
		if (this.tessControl != null) {
			this.tessControl.attachToShader(this);
		}
		if (this.tessEval != null) {
			this.tessEval.attachToShader(this);
		}
	}

	@Override
	public void iris$createExtraShaders(ResourceProvider factory, ResourceLocation name) {
		factory.getResource(new ResourceLocation(name.getNamespace(), name.getPath() + "_geometry.gsh")).ifPresent(geometry -> {
			try {
				this.geometry = Program.compileShader(IrisProgramTypes.GEOMETRY, name.getPath(), geometry.open(), geometry.sourcePackId(), new GlslPreprocessor() {
					@Nullable
					@Override
					public String applyImport(boolean bl, String string) {
						return null;
					}
				});
				GLDebug.nameObject(KHRDebug.GL_SHADER, this.geometry.getId(), name.getPath() + "_geometry.gsh");
			} catch (IOException e) {
				Iris.logger.error("Failed to create shader program", e);
			}
		});
		factory.getResource(new ResourceLocation(name.getNamespace(), name.getPath() + "_tessControl.tcs")).ifPresent(tessControl -> {
			try {
				this.tessControl = Program.compileShader(IrisProgramTypes.TESS_CONTROL, name.getPath(), tessControl.open(), tessControl.sourcePackId(), new GlslPreprocessor() {
					@Nullable
					@Override
					public String applyImport(boolean bl, String string) {
						return null;
					}
				});
				GLDebug.nameObject(KHRDebug.GL_SHADER, this.tessControl.getId(), name.getPath() + "_tessControl.tcs");
			} catch (IOException e) {
				Iris.logger.error("Failed to create shader program", e);
			}
		});
		factory.getResource(new ResourceLocation(name.getNamespace(), name.getPath() + "_tessEval.tes")).ifPresent(tessEval -> {
			try {
				this.tessEval = Program.compileShader(IrisProgramTypes.TESS_EVAL, name.getPath(), tessEval.open(), tessEval.sourcePackId(), new GlslPreprocessor() {
					@Nullable
					@Override
					public String applyImport(boolean bl, String string) {
						return null;
					}
				});
				GLDebug.nameObject(KHRDebug.GL_SHADER, this.tessEval.getId(), name.getPath() + "_tessEval.tes");
			} catch (IOException e) {
				Iris.logger.error("Failed to create shader program", e);
			}
		});
	}

	public Program getGeometry() {
		return this.geometry;
	}

	public Program getTessControl() {
		return this.tessControl;
	}

	public Program getTessEval() {
		return this.tessEval;
	}

	public boolean hasActiveImages() {
		return images.getActiveImages() > 0;
	}
}
