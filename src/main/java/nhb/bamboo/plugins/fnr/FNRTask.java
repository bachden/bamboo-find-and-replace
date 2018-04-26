package nhb.bamboo.plugins.fnr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.task.CommonTaskContext;
import com.atlassian.bamboo.task.CommonTaskType;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.variable.VariableDefinitionContext;

public class FNRTask implements CommonTaskType {

	private static final String[] REGEX_SPECIAL_CHARS = new String[] { "\\", ".", "*", "+", "-", "[", "]", "(", ")",
			"$", "^", "|", "{", "}", "?" };

	private static final String normalizeForRegex(String key) {
		String result = key;
		for (String c : REGEX_SPECIAL_CHARS) {
			result = result.replaceAll("\\" + c, "\\\\\\" + c);
		}
		return result;
	}

	private static final String findAndReplace(String content, String find, String replaceWith, boolean ignoreCase,
			boolean isUsingRegex) {
		if (isUsingRegex) {
			return content.replaceAll((ignoreCase ? "(?i)" : "") + find, replaceWith);
		} else {
			return content.replaceAll((ignoreCase ? "(?i)" : "") + normalizeForRegex(find), replaceWith);
		}
	}

	private static final String readTextContentFromFile(String filePath) throws Exception {
		File file = new File(filePath);

		try (InputStream is = new FileInputStream(file); StringWriter sw = new StringWriter()) {
			IOUtils.copy(is, sw);
			return sw.toString();
		}
	}

	private static final void writeTextContentToFile(String content, String filePath) throws Exception {
		File file = new File(filePath);
		if (!file.exists()) {
			file.createNewFile();
		}

		try (OutputStream os = new FileOutputStream(file)) {
			IOUtils.write(content, os);
		}
	}

	private static final Set<File> scanForFiles(String workingFolder, String wildcardFileName) {
		File dir = new File(workingFolder);
		if (!dir.exists() || !dir.isDirectory()) {
			throw new IllegalArgumentException("Working directory must exist and is a folder");
		}

		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setIncludes(new String[] { wildcardFileName });
		scanner.setBasedir(dir);
		scanner.setCaseSensitive(false);
		scanner.scan();

		Set<File> results = new HashSet<>();
		for (String fileName : scanner.getIncludedFiles()) {
			results.add(dir.toPath().resolve(fileName).toFile());
		}
		return results;
	}

	private static final Collection<String[]> extractVariableNameGroups(String text) {
		Collection<String[]> results = new LinkedList<>();
		Pattern pattern = Pattern.compile("\\$\\{([A-Za-z0-9\\._]+)\\}");
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			results.add(new String[] { matcher.group(0), matcher.group(1) });
		}
		return results;
	}

	@Override
	public TaskResult execute(CommonTaskContext taskContext) throws TaskException {
		final BuildLogger logger = taskContext.getBuildLogger();

		Map<String, VariableDefinitionContext> effectiveVariables = taskContext.getCommonContext().getVariableContext()
				.getEffectiveVariables();

		final boolean ignoreCase = taskContext.getConfigurationMap().getAsBoolean("nonCaseSensitive");
		final boolean isUsingRegex = taskContext.getConfigurationMap().getAsBoolean("regex");
		final String findStr = taskContext.getConfigurationMap().get("find");

		String replaceWithStr = taskContext.getConfigurationMap().get("replaceWith").trim();
		Collection<String[]> extractedVariableNameGroups = extractVariableNameGroups(replaceWithStr);
		if (extractedVariableNameGroups.size() > 0) {
			Map<String, String> map = new HashMap<>();
			Collection<VariableDefinitionContext> variables = effectiveVariables.values();
			for (final VariableDefinitionContext vdc : variables) {
				map.put(vdc.getKey(), vdc.getValue());
			}
			// logger.addBuildLogEntry("All variables: " + map);

			for (String[] group : extractedVariableNameGroups) {
				String value = map.get(group[1]);
				// logger.addBuildLogEntry("Replace " + group[0] + " by " + value);
				replaceWithStr = replaceWithStr.replaceAll(normalizeForRegex(group[0]), value);
			}
			// logger.addBuildLogEntry("Found variables in replacement string, convert: "
			// + taskContext.getConfigurationMap().get("replaceWith").trim() + " -> " +
			// replaceWithStr);
		}

		// logger.addBuildLogEntry("Findding " + findStr + " and replace with: " +
		// replaceWithStr + ", case sensitive: "
		// + !ignoreCase + ", using regex: " + isUsingRegex);

		String strWildcards = taskContext.getConfigurationMap().get("filePath");
		String[] wildcards = strWildcards.split("\\r?\\n");
		String workingDir = taskContext.getWorkingDirectory().getAbsolutePath();

		for (String wildcard : wildcards) {
			wildcard = wildcard.trim();
			if (wildcard.length() > 0) {
				Set<File> matchedFiles = scanForFiles(workingDir, wildcard);
				for (File file : matchedFiles) {
					final String filePath = file.getAbsolutePath();
					String content = null;
					try {
						content = readTextContentFromFile(filePath);
					} catch (Exception e) {
						throw new TaskException("Read file error", e);
					}

					if (content != null) {
						logger.addBuildLogEntry("****** Process 'find and replace' on file: " + filePath);

						String[] lines = content.split("\\r?\\n");
						for (int i = 0; i < lines.length; i++) {
							lines[i] = findAndReplace(lines[i], findStr, replaceWithStr, ignoreCase, isUsingRegex);
						}

						String newContent = StringUtils.join(lines, "\n");

						// logger.addBuildLogEntry("old content: \n" + content);
						// logger.addBuildLogEntry("new content: \n" + newContent);

						try {
							writeTextContentToFile(newContent, filePath);
						} catch (Exception e) {
							throw new TaskException("Write file error", e);
						}
					}
				}
			} else {
				throw new TaskException("File path invalid");
			}
		}

		return TaskResultBuilder.newBuilder(taskContext).success().build();
	}

	public static void main(String[] args) {
		String text = "${TEST_VARIABLE_1} - ${TEST_VARIABLE_2}";
		System.out.println(extractVariableNameGroups(text));
	}
}