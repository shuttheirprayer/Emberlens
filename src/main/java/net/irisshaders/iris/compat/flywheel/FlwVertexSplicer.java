package net.irisshaders.iris.compat.flywheel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FlwVertexSplicer {
	private static final Pattern VERSION = Pattern.compile("^\\s*#version\\s+(\\d+)");
	private static final Pattern EXTENSION = Pattern.compile("^\\s*#extension\\b");
	private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
	private static final Pattern INPUT = Pattern.compile("^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective|centroid)\\s+)*in\\s+(\\w+)\\s+(\\w+)\\s*;\\s*$");
	private static final Pattern OUTPUT = Pattern.compile("^((?:flat|smooth|noperspective|centroid)\\s+)*out\\s+");
	private static final Pattern IRIS_ATTRIBUTE = Pattern.compile("\\biris_(Position|Color|UV0|UV1|UV2|Normal)\\b");
	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
	private static final Pattern DECLARATION = Pattern.compile("^(?:#define\\s+(\\w+)|struct\\s+(\\w+)|(?!(?:return|else|case|if|for|while|switch|do|discard|break|continue|layout)\\b)(?:(?:const|uniform|flat|smooth|out|in|highp|mediump|lowp)\\s+)*\\w+\\s+(\\w+)\\s*[(=;\\[])");
	private static final String[][] ATTRIBUTES = {
		{"_flw_aPos", "iris_Position"},
		{"_flw_aColor", "iris_Color"},
		{"_flw_aTexCoord", "iris_UV0"},
		{"_flw_aOverlay", "iris_UV1"},
		{"_flw_aLight", "iris_UV2"},
		{"_flw_aNormal", "iris_Normal"}
	};
	private static final Map<String, String> INPUT_VALUES = Map.ofEntries(
		Map.entry("iris_Position", "vec4(flw_vertexPos.xyz - flw_cameraPos, 1.0)"),
		Map.entry("iris_Color", "flw_vertexColor"),
		Map.entry("iris_UV0", "vec4(flw_vertexTexCoord, 0.0, 1.0)"),
		Map.entry("iris_UV1", "vec4(vec2(flw_vertexOverlay), 0.0, 1.0)"),
		Map.entry("iris_UV2", "vec4(floor(flw_vertexLight * 256.0 + 0.5), 0.0, 1.0)"),
		Map.entry("iris_Normal", "vec4(flw_vertexNormal, 0.0)"),
		Map.entry("iris_Entity", "vec4(-1.0)"),
		Map.entry("mc_Entity", "vec4(-1.0)"),
		Map.entry("mc_midTexCoord", "vec4(flw_vertexTexCoord, 0.0, 1.0)"),
		Map.entry("at_tangent", "vec4(_ember_tangent(flw_vertexNormal), 1.0)"),
		Map.entry("at_midBlock", "vec4(0.0)")
	);

	private FlwVertexSplicer() {
	}

	public static String splice(String iris, String flywheel) {
		Set<String> extensions = new LinkedHashSet<>();
		int version = 410;
		StringBuilder irisBody = new StringBuilder();
		for (String line : iris.split("\n", -1)) {
			Matcher v = VERSION.matcher(line);
			if (v.find()) {
				version = Math.max(version, Integer.parseInt(v.group(1)));
			} else if (EXTENSION.matcher(line).find()) {
				extensions.add(line.trim());
			} else {
				irisBody.append(line).append('\n');
			}
		}

		Map<String, String> inputs = new LinkedHashMap<>();
		StringBuilder irisOut = new StringBuilder();
		for (String line : irisBody.toString().split("\n", -1)) {
			Matcher in = INPUT.matcher(line);
			if (in.matches()) {
				inputs.put(in.group(2), in.group(1));
				irisOut.append(in.group(1)).append(' ').append(in.group(2)).append(";\n");
			} else {
				irisOut.append(line).append('\n');
			}
		}
		String irisCode = MAIN.matcher(irisOut).replaceAll("void _ember_irisMain()");
		irisCode = IRIS_ATTRIBUTE.matcher(irisCode).replaceAll("_ember_$1");

		StringBuilder flwBody = new StringBuilder();
		for (String line : flywheel.split("\n", -1)) {
			if (VERSION.matcher(line).find()) {
				continue;
			}
			if (EXTENSION.matcher(line).find()) {
				extensions.add(line.trim());
				continue;
			}
			flwBody.append(OUTPUT.matcher(line).replaceFirst("")).append('\n');
		}
		String flwCode = flwBody.toString();
		for (String[] attribute : ATTRIBUTES) {
			flwCode = flwCode.replaceAll("\\b" + attribute[0] + "\\b", attribute[1]);
		}
		flwCode = MAIN.matcher(flwCode).replaceAll("void _ember_flwMain()");

		Set<String> irisNames = identifiers(irisCode);
		List<String> clashes = new ArrayList<>();
		for (String declared : declarations(flwCode)) {
			if (irisNames.contains(declared) && !declared.startsWith("_flw") && !declared.startsWith("_Flw") && !declared.startsWith("_FLW") && !declared.startsWith("iris_")) {
				clashes.add(declared);
			}
		}

		StringBuilder main = new StringBuilder();
		main.append("vec3 _ember_tangent(vec3 n) {\n");
		main.append("    vec3 a = abs(n.y) > 0.9 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);\n");
		main.append("    return normalize(cross(a, n));\n");
		main.append("}\n");
		main.append("void main() {\n");
		main.append("    _ember_flwMain();\n");
		main.append("#ifdef FLW_EMBEDDED\n");
		main.append("    FlwLightAo _ember_light;\n");
		main.append("    if (flw_light(flw_vertexPos.xyz, flw_vertexNormal, _ember_light)) {\n");
		main.append("        flw_vertexLight = max(flw_vertexLight, _ember_light.light);\n");
		main.append("        flw_vertexColor.rgb *= _ember_light.ao;\n");
		main.append("    }\n");
		main.append("#endif\n");
		for (Map.Entry<String, String> input : inputs.entrySet()) {
			String name = IRIS_ATTRIBUTE.matcher(input.getKey()).replaceAll("_ember_$1");
			String value = INPUT_VALUES.getOrDefault(input.getKey(), "vec4(0.0)");
			main.append("    ").append(name).append(" = ").append(input.getValue()).append('(').append(value).append(");\n");
		}
		main.append("    _ember_irisMain();\n");
		main.append("}\n");

		String flwPart = flwCode + main;
		if (!clashes.isEmpty()) {
			Pattern rename = Pattern.compile("(?<![.\\w])(" + String.join("|", clashes) + ")\\b");
			flwPart = rename.matcher(flwPart).replaceAll("_ember_$1");
		}

		StringBuilder out = new StringBuilder();
		out.append("#version ").append(version).append(" core\n");
		for (String extension : extensions) {
			out.append(extension).append('\n');
		}
		out.append(irisCode).append('\n').append(flwPart);
		return out.toString();
	}

	private static Set<String> identifiers(String code) {
		Set<String> names = new HashSet<>();
		Matcher m = IDENTIFIER.matcher(code);
		while (m.find()) {
			names.add(m.group());
		}
		return names;
	}

	private static Set<String> declarations(String code) {
		Set<String> names = new LinkedHashSet<>();
		for (String line : code.split("\n")) {
			Matcher m = DECLARATION.matcher(line);
			if (m.find()) {
				for (int g = 1; g <= 3; g++) {
					if (m.group(g) != null) {
						names.add(m.group(g));
					}
				}
			}
		}
		names.remove("main");
		return names;
	}
}
