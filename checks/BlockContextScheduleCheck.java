import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class BlockContextScheduleCheck {

	private static final int BLOCK_RENDER_TYPE = -1;
	private static final int FLUID_RENDER_TYPE = 1;

	private static final class Holder {
		int localPosX, localPosY, localPosZ;
		int blockId = -1;
		int renderType = -1;
		int lightValue;
		boolean ignoreMidBlock;

		void setLocalPos(int x, int y, int z) {
			this.localPosX = x;
			this.localPosY = y;
			this.localPosZ = z;
		}

		void set(int blockId, int renderType, int lightValue) {
			this.blockId = blockId;
			this.renderType = renderType;
			this.lightValue = lightValue;
		}

		void reset() {
			this.blockId = -1;
			this.renderType = -1;
			this.localPosX = 0;
			this.localPosY = 0;
			this.localPosZ = 0;
			this.lightValue = 0;
		}

		String snapshot() {
			return localPosX + "," + localPosY + "," + localPosZ + "," + blockId + "," + renderType + ","
				+ lightValue + "," + ignoreMidBlock;
		}
	}

	private static final class Block {
		final int x, y, z;
		final boolean air;
		final boolean invisible;
		final boolean model;
		final boolean fluid;
		final boolean blockEntity;
		final boolean lightBlock;
		final int id;
		final int light;

		Block(int x, int y, int z, boolean air, boolean invisible, boolean model, boolean fluid,
			  boolean blockEntity, boolean lightBlock, int id, int light) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.air = air;
			this.invisible = invisible;
			this.model = model;
			this.fluid = fluid;
			this.blockEntity = blockEntity;
			this.lightBlock = lightBlock;
			this.id = id;
			this.light = light;
		}
	}

	private static void hookRenderShape(Holder holder, Block block, int posX, int posY, int posZ,
										boolean voxelize, List<String> pushes) {
		if (voxelize && block.lightBlock) {
			holder.setLocalPos(0, 0, 0);
			holder.ignoreMidBlock = true;
			holder.set(0, 0, block.light);
			pushes.add("light@" + (posX & 15) + "," + (posY & 15) + "," + (posZ & 15) + ":" + holder.snapshot());
			holder.ignoreMidBlock = false;
			return;
		}

		holder.setLocalPos(posX, posY, posZ);
	}

	private static List<String> oldSchedule(List<Block> blocks, boolean voxelize) {
		Holder holder = new Holder();
		List<String> pushes = new ArrayList<>();

		int posX = 0, posY = 0, posZ = 0;

		for (Block block : blocks) {
			if (block.air) {
				hookRenderShape(holder, block, posX, posY, posZ, voxelize, pushes);

				if (block.invisible) {
					holder.reset();

					if (!block.blockEntity) {
						continue;
					}
				}
			}

			posX = block.x;
			posY = block.y;
			posZ = block.z;

			hookRenderShape(holder, block, posX, posY, posZ, voxelize, pushes);

			if (block.model) {
				holder.set(block.id, BLOCK_RENDER_TYPE, block.light);
				pushes.add("model:" + holder.snapshot());
			}

			if (block.fluid) {
				holder.set(block.id, FLUID_RENDER_TYPE, block.light);
				pushes.add("fluid:" + holder.snapshot());
			}

			holder.reset();
		}

		pushes.add("end:" + holder.snapshot());
		return pushes;
	}

	private static List<String> newSchedule(List<Block> blocks, boolean voxelize) {
		Holder holder = new Holder();
		List<String> pushes = new ArrayList<>();

		for (Block block : blocks) {
			if (block.air && block.invisible) {
				holder.reset();

				if (!block.blockEntity) {
					continue;
				}
			}

			hookRenderShape(holder, block, block.x, block.y, block.z, voxelize, pushes);

			if (block.model) {
				holder.set(block.id, BLOCK_RENDER_TYPE, block.light);
				pushes.add("model:" + holder.snapshot());
			}

			if (block.fluid) {
				holder.set(block.id, FLUID_RENDER_TYPE, block.light);
				pushes.add("fluid:" + holder.snapshot());
			}

			holder.reset();
		}

		pushes.add("end:" + holder.snapshot());
		return pushes;
	}

	private static List<Block> section(Random random) {
		List<Block> blocks = new ArrayList<>();

		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					boolean air = random.nextInt(10) < 6;
					boolean lightBlock = !air && random.nextInt(40) == 0;
					boolean model = !air && !lightBlock && random.nextInt(10) < 8;
					boolean fluid = !air && !lightBlock && random.nextInt(10) < 3;
					boolean blockEntity = random.nextInt(20) == 0;
					boolean invisible = air || lightBlock;
					blocks.add(new Block(x - 32, y + 48, z + 16, air, invisible, model, fluid,
						blockEntity, lightBlock, random.nextInt(4096), random.nextInt(16)));
				}
			}
		}

		return blocks;
	}

	public static void main(String[] args) {
		Random random = new Random(20260909L);

		for (int round = 0; round < 64; round++) {
			boolean voxelize = (round & 1) == 0;
			List<Block> blocks = section(random);

			List<String> before = oldSchedule(blocks, voxelize);
			List<String> after = newSchedule(blocks, voxelize);

			for (int i = 0; i < Math.max(before.size(), after.size()); i++) {
				String a = i < before.size() ? before.get(i) : "<missing>";
				String b = i < after.size() ? after.get(i) : "<missing>";

				if (!a.equals(b)) {
					throw new AssertionError("round " + round + " push " + i + ": was " + a + ", now " + b);
				}
			}

			if (before.size() < 64) {
				throw new AssertionError("round " + round + " produced " + (before.size() - 1)
					+ " pushes, the check is not exercising anything");
			}
		}

		System.out.println("BlockContextScheduleCheck: ok");
	}
}
