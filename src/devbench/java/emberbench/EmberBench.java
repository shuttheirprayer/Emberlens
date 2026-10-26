package emberbench;

import com.mojang.blaze3d.platform.Window;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = "oculus", value = Dist.CLIENT)
public final class EmberBench {
	private static final Logger LOG = LoggerFactory.getLogger("EmberBench");
	private static final Path SCRIPT = Path.of("emberbench.txt");
	private static final Path RESULTS = Path.of("emberbench_results.txt");

	private static List<String> steps;
	private static int index;
	private static int waiting;
	private static boolean worldRequested;
	private static String measureName;
	private static long measureEnd;
	private static final long[] frames = new long[200000];
	private static int frameCount;
	private static long lastFrame;
	private static String pendingShot;

	private EmberBench() {
	}

	private static int idleTicks;

	static {
		LOG.info("[emberbench] loaded, script present: {}", Files.exists(SCRIPT));
	}

	private static void createWorld(Minecraft mc) {
		worldRequested = true;
		try {
			steps = new ArrayList<>(Files.readAllLines(SCRIPT));
		} catch (IOException e) {
			LOG.error("script", e);
			return;
		}
		LevelSettings settings = new LevelSettings("bench", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
			WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel("bench", settings, new WorldOptions(1L, false, false),
			access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!worldRequested && mc.level == null && Files.exists(SCRIPT)) {
			idleTicks++;
			if (idleTicks % 100 == 0) {
				LOG.info("[emberbench] waiting on screen {}", mc.screen == null ? "none" : mc.screen.getClass().getName());
			}
			if (idleTicks >= 100 && mc.getOverlay() == null) {
				createWorld(mc);
			}
			return;
		}
		if (steps == null) {
			return;
		}
		if (mc.level == null || mc.player == null || measureName != null || pendingShot != null) {
			return;
		}
		if (waiting > 0) {
			waiting--;
			return;
		}
		MinecraftServer server = mc.getSingleplayerServer();
		while (index < steps.size()) {
			String line = steps.get(index++).trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int space = line.indexOf(' ');
			String op = space < 0 ? line : line.substring(0, space);
			String arg = space < 0 ? "" : line.substring(space + 1);
			switch (op) {
				case "cmd" -> {
					if (server != null) {
						server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), arg));
					}
				}
				case "ccmd" -> ClientCommandHandler.runCommand(arg);
				case "wait" -> {
					waiting = Integer.parseInt(arg);
					return;
				}
				case "backend" -> {
					Backend backend = BackendManager.currentBackend();
					result("backend " + arg + " " + Backend.REGISTRY.getIdOrThrow(backend));
				}
				case "measure" -> {
					String[] parts = arg.split(" ");
					measureName = parts[0];
					frameCount = 0;
					lastFrame = 0;
					measureEnd = System.nanoTime() + (long) (Double.parseDouble(parts[1]) * 1e9);
					return;
				}
				case "shot" -> {
					pendingShot = arg;
					return;
				}
				case "pack" -> {
					try {
						boolean off = arg.equals("off");
						net.irisshaders.iris.Iris.getIrisConfig().setShadersEnabled(!off);
						if (!off) {
							net.irisshaders.iris.Iris.getIrisConfig().setShaderPackName(arg);
						}
						net.irisshaders.iris.Iris.getIrisConfig().save();
						net.irisshaders.iris.Iris.reload();
						result("pack " + arg);
					} catch (IOException e) {
						LOG.error("pack", e);
					}
				}
				case "quit" -> {
					mc.stop();
					return;
				}
				default -> LOG.warn("unknown step {}", line);
			}
		}
	}

	@SubscribeEvent
	public static void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (pendingShot != null) {
			Screenshot.grab(mc.gameDirectory, pendingShot + ".png", mc.getMainRenderTarget(), c -> {
			});
			result("shot " + pendingShot);
			pendingShot = null;
			return;
		}
		if (measureName == null) {
			return;
		}
		long now = System.nanoTime();
		if (lastFrame != 0 && frameCount < frames.length) {
			frames[frameCount++] = now - lastFrame;
		}
		lastFrame = now;
		if (now >= measureEnd) {
			report(mc);
			measureName = null;
		}
	}

	private static void report(Minecraft mc) {
		long[] sorted = Arrays.copyOf(frames, frameCount);
		Arrays.sort(sorted);
		long total = 0;
		for (long f : sorted) {
			total += f;
		}
		double avgMs = frameCount == 0 ? 0 : total / 1e6 / frameCount;
		double low1 = average(sorted, Math.max(1, frameCount / 100));
		double low01 = average(sorted, Math.max(1, frameCount / 1000));
		Window window = mc.getWindow();
		result(String.format("measure %s frames %d avg %.1f fps (%.2f ms) 1%%low %.1f fps 0.1%%low %.1f fps window %dx%d", measureName, frameCount,
			1000.0 / avgMs, avgMs, 1000.0 / low1, 1000.0 / low01, window.getWidth(), window.getHeight()));
	}

	private static double average(long[] sorted, int worst) {
		long sum = 0;
		for (int i = sorted.length - worst; i < sorted.length; i++) {
			sum += sorted[i];
		}
		return sum / 1e6 / worst;
	}

	private static void result(String line) {
		LOG.info("[emberbench] {}", line);
		try {
			Files.writeString(RESULTS, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			LOG.error("results", e);
		}
	}
}
