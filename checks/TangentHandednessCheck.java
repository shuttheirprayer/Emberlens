import java.util.Random;

public final class TangentHandednessCheck {
	private static float lastDot;
	private static float lastMagnitude;

	private static float rsqrt(float value) {
		if (value == 0.0f) {
			return 1.0f;
		}

		return (float) (1.0 / Math.sqrt(value));
	}

	private static float tangentW(float normalX, float normalY, float normalZ,
								  float x0, float y0, float z0, float u0, float v0,
								  float x1, float y1, float z1, float u1, float v1,
								  float x2, float y2, float z2, float u2, float v2,
								  boolean normalizeBitangent) {
		float edge1x = x1 - x0;
		float edge1y = y1 - y0;
		float edge1z = z1 - z0;

		float edge2x = x2 - x0;
		float edge2y = y2 - y0;
		float edge2z = z2 - z0;

		float deltaU1 = u1 - u0;
		float deltaV1 = v1 - v0;
		float deltaU2 = u2 - u0;
		float deltaV2 = v2 - v0;

		float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
		float f = fdenom == 0.0f ? 1.0f : 1.0f / fdenom;

		float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
		float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
		float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
		float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
		tangentx *= tcoeff;
		tangenty *= tcoeff;
		tangentz *= tcoeff;

		float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
		float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
		float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);

		if (normalizeBitangent) {
			float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
			bitangentx *= bitcoeff;
			bitangenty *= bitcoeff;
			bitangentz *= bitcoeff;
		}

		float pbitangentx = tangenty * normalZ - tangentz * normalY;
		float pbitangenty = tangentz * normalX - tangentx * normalZ;
		float pbitangentz = tangentx * normalY - tangenty * normalX;

		float dot = (bitangentx * pbitangentx) + (bitangenty * pbitangenty) + (bitangentz * pbitangentz);

		lastDot = dot;
		lastMagnitude = (float) Math.sqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz)
			* (float) Math.sqrt(pbitangentx * pbitangentx + pbitangenty * pbitangenty + pbitangentz * pbitangentz);

		return dot < 0 ? -1.0f : 1.0f;
	}

	public static void main(String[] args) {
		Random random = new Random(1234);

		int cases = 5_000_000;
		int mismatches = 0;
		float worstRelative = 0.0f;

		for (int i = 0; i < cases; i++) {
			float[] p = new float[15];

			for (int j = 0; j < 15; j++) {
				p[j] = (random.nextFloat() - 0.5f) * (j % 5 < 3 ? 64.0f : 1.0f);
			}

			if (i % 97 == 0) {
				p[5] = p[0];
				p[6] = p[1];
				p[7] = p[2];
			}

			float nx = random.nextFloat() - 0.5f;
			float ny = random.nextFloat() - 0.5f;
			float nz = random.nextFloat() - 0.5f;
			float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

			if (nl != 0.0f) {
				nx /= nl;
				ny /= nl;
				nz /= nl;
			}

			float normalized = tangentW(nx, ny, nz, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], p[12], p[13], p[14], true);
			float raw = tangentW(nx, ny, nz, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], p[12], p[13], p[14], false);

			if (normalized != raw) {
				mismatches++;

				float relative = lastMagnitude == 0.0f ? 0.0f : Math.abs(lastDot) / lastMagnitude;

				if (relative > worstRelative) {
					worstRelative = relative;
				}
			}
		}

		System.out.println("cases=" + cases + " mismatches=" + mismatches + " worstRelative=" + worstRelative);

		if (worstRelative > 1.0e-6f) {
			throw new AssertionError("handedness differs outside float precision: " + worstRelative);
		}

		System.out.println("PASS");
	}
}
