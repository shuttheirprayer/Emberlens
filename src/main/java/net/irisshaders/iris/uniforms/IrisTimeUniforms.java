package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.time.LocalDateTime;

public class IrisTimeUniforms {
	private static LocalDateTime dateTime;

	static {
	}

	public static void updateTime() {
		dateTime = null;
	}

	private static LocalDateTime dateTime() {
		LocalDateTime now = dateTime;

		if (now == null) {
			dateTime = now = LocalDateTime.now();
		}

		return now;
	}

	public static void addTimeUniforms(UniformHolder uniforms) {
		Vector3i date = new Vector3i();
		Vector3i time = new Vector3i();
		Vector2i yearTime = new Vector2i();
		uniforms.uniform3i(UniformUpdateFrequency.PER_TICK, "currentDate", () -> {
			LocalDateTime now = dateTime();
			return date.set(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
		});
		uniforms.uniform3i(UniformUpdateFrequency.PER_TICK, "currentTime", () -> {
			LocalDateTime now = dateTime();
			return time.set(now.getHour(), now.getMinute(), now.getSecond());
		});
		uniforms.uniform2i(UniformUpdateFrequency.PER_TICK, "currentYearTime", () -> {
			LocalDateTime now = dateTime();
			int secondOfYear = ((now.getDayOfYear() - 1) * 86400) + (now.getHour() * 3600) + (now.getMinute() * 60) + now.getSecond();
			return yearTime.set(secondOfYear, (now.toLocalDate().lengthOfYear() * 86400) - secondOfYear);
		});
	}
}
