package nhb.bamboo.plugins.fnr;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;

@Component
public class FNRConfigurator extends AbstractTaskConfigurator {

	private final I18nResolver i18nResolver;

	private static final List<String> FIELDS = Arrays
			.asList(new String[] { "filePath", "find", "nonCaseSensitive", "regex", "replaceWith" });

	@Autowired
	public FNRConfigurator(@ComponentImport("i18n") final I18nResolver i18nResolver) {
		this.i18nResolver = i18nResolver;
	}

	@NotNull
	public Map<String, String> generateTaskConfigMap(@NotNull final ActionParametersMap params,
			@Nullable final TaskDefinition prfnrousTaskDefinition) {
		final Map<String, String> config = (Map<String, String>) super.generateTaskConfigMap(params,
				prfnrousTaskDefinition);
		for (String field : FIELDS) {
			config.put(field, params.getString(field));
		}
		return config;
	}

	public void populateContextForCreate(@NotNull final Map<String, Object> context) {
		super.populateContextForCreate(context);
	}

	public void populateContextForEdit(@NotNull final Map<String, Object> context,
			@NotNull final TaskDefinition taskDefinition) {
		super.populateContextForEdit(context, taskDefinition);
		for (String field : FIELDS) {
			context.put(field, taskDefinition.getConfiguration().get(field));
		}
	}

	public void validate(@NotNull final ActionParametersMap params, @NotNull final ErrorCollection errorCollection) {
		super.validate(params, errorCollection);
		final String sayValue = params.getString("filePath");
		if (StringUtils.isBlank((CharSequence) sayValue)) {
			errorCollection.addError("filePath",
					this.i18nResolver.getText("nhb.bamboo.plugins.fnr.text.filePathError"));
		}

		final String findValue = params.getString("find");
		if (StringUtils.isBlank((CharSequence) findValue)) {
			errorCollection.addError("find", this.i18nResolver.getText("nhb.bamboo.plugins.fnr.text.findInvalid"));
		}
	}
}
