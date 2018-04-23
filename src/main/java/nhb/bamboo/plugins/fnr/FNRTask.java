package nhb.bamboo.plugins.fnr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.task.CommonTaskContext;
import com.atlassian.bamboo.task.CommonTaskType;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.variable.VariableDefinitionContext;

public class FNRTask implements CommonTaskType {

	private static final String[] REGEX_SPECIAL_CHARS = new String[] { ".", "*", "+", "-", "[", "]", "(", ")", "$", "^",
			"|", "{", "}", "?" };

	private static final String normalizeForRegex(String key) {
		String result = key;
		result = result.replaceAll("\\\\", "\\\\\\\\");
		for (String c : REGEX_SPECIAL_CHARS) {
			result = result.replaceAll("\\" + c, "\\\\" + c);
		}
		return result;
	}

	private static final String findAndReplace(String content, String find, String replaceWith,
			boolean nonCaseSensitive, boolean regex) {
		if (regex) {
			return content.replaceAll((nonCaseSensitive ? "(?i)" : "") + find, replaceWith);
		} else {
			return content.replaceAll((nonCaseSensitive ? "(?i)" : "") + normalizeForRegex(find), replaceWith);
		}
	}

	private static final String getTextFileContent(String filePath) throws Exception {
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

	@Override
	public TaskResult execute(CommonTaskContext taskContext) throws TaskException {
		final BuildLogger logger = taskContext.getBuildLogger();

		String filePath = taskContext.getConfigurationMap().get("filePath");
		filePath = filePath.trim();
		if (filePath.length() > 0) {
			filePath = taskContext.getWorkingDirectory() + "/" + filePath;
			logger.addBuildLogEntry("File to be processed: " + filePath);

			String content = null;
			try {
				content = getTextFileContent(filePath);

			} catch (Exception e) {
				throw new TaskException("Read file error", e);
			}

			Map<String, VariableDefinitionContext> effectiveVariables = taskContext.getCommonContext()
					.getVariableContext().getEffectiveVariables();

			Map<String, String> map = new HashMap<>();
			Collection<VariableDefinitionContext> variables = effectiveVariables.values();
			for (final VariableDefinitionContext vdc : variables) {
				map.put(vdc.getKey(), vdc.getValue());
			}

			logger.addBuildLogEntry("Variables: " + map);

			String findStr = taskContext.getConfigurationMap().get("find");
			String replaceWithStr = taskContext.getConfigurationMap().get("replaceWith").trim();
			if (replaceWithStr.startsWith("${") && replaceWithStr.endsWith("}")) {
				int length = replaceWithStr.length();
				String variableName = replaceWithStr.substring(2).substring(0, length - 3);
				replaceWithStr = map.get(variableName);
			}
			logger.addBuildLogEntry("Find " + findStr + " -> replace with: " + replaceWithStr);

			boolean nonCaseSensitive = taskContext.getConfigurationMap().getAsBoolean("nonCaseSensitive");
			boolean regex = taskContext.getConfigurationMap().getAsBoolean("regex");

			String newContent = findAndReplace(content, findStr, replaceWithStr, nonCaseSensitive, regex);

			try {
				writeTextContentToFile(newContent, filePath);
			} catch (Exception e) {
				throw new TaskException("Write file error", e);
			}
		} else {
			throw new TaskException("File path invalid");
		}

		return TaskResultBuilder.newBuilder(taskContext).success().build();
	}

	public static void main(String[] args) {
		// String str = "${TEST_VARIABLE}";
		// if (str.startsWith("${") && str.endsWith("}")) {
		// int length = str.length();
		// str = str.substring(2).substring(0, length - 3);
		// }
		// System.out.println(str);
		String str = "foo\\d+";
		System.out.println(normalizeForRegex(str));
	}
}