import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class VertexMapOrderCheck {
	private static List<String> keysOf(Map<String, String> map) {
		return new ArrayList<>(map.keySet());
	}

	public static void main(String[] args) {
		Random random = new Random(20260909L);

		int rounds = 200_000;
		int keySpace = 40;

		Map<String, String> reference = new LinkedHashMap<>(16);
		Map<String, String> candidate = new Object2ObjectLinkedOpenHashMap<>(16);

		for (int round = 0; round < rounds; round++) {
			String key = "k" + random.nextInt(keySpace);
			int op = random.nextInt(10);

			if (op < 5) {
				String value = "v" + random.nextInt(4);
				Object a = reference.put(key, value);
				Object b = candidate.put(key, value);

				if (a == null ? b != null : !a.equals(b)) {
					throw new AssertionError("put returned different previous value at round " + round);
				}
			} else if (op < 7) {
				Object a = reference.remove(key);
				Object b = candidate.remove(key);

				if (a == null ? b != null : !a.equals(b)) {
					throw new AssertionError("remove returned different value at round " + round);
				}
			} else if (op < 9) {
				Object a = reference.get(key);
				Object b = candidate.get(key);

				if (a == null ? b != null : !a.equals(b)) {
					throw new AssertionError("get differs at round " + round);
				}

				if (reference.containsKey(key) != candidate.containsKey(key)) {
					throw new AssertionError("containsKey differs at round " + round);
				}
			} else {
				reference.clear();
				candidate.clear();
			}

			if (reference.size() != candidate.size()) {
				throw new AssertionError("size differs at round " + round);
			}

			if (reference.isEmpty() != candidate.isEmpty()) {
				throw new AssertionError("isEmpty differs at round " + round);
			}

			if (!keysOf(reference).equals(keysOf(candidate))) {
				throw new AssertionError("iteration order diverged at round " + round
					+ NL() + "  reference=" + keysOf(reference)
					+ NL() + "  candidate=" + keysOf(candidate));
			}
		}

		System.out.println("rounds=" + rounds + " finalSize=" + reference.size());
		System.out.println("PASS");
	}

	private static String NL() {
		return System.lineSeparator();
	}
}
