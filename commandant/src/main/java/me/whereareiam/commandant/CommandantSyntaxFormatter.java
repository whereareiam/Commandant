package me.whereareiam.commandant;

import me.whereareiam.commandant.model.message.HelpMessages;
import me.whereareiam.keystone.model.SerializerOptions;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.parser.aggregate.AggregateParser;
import org.incendo.cloud.syntax.StandardCommandSyntaxFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

/**
 * Cloud syntax formatter that mirrors Commandant help argument formatting.
 *
 * @param <C> command sender type
 */
public class CommandantSyntaxFormatter<C> extends StandardCommandSyntaxFormatter<C> {
	private final Map<String, String> argumentNames;
	private final HelpMessages.Format argumentFormat;
	private final SerializerOptions.PlaceholderFormat placeholderFormat;

	/**
	 * Creates a new formatter.
	 *
	 * @param manager           command manager
	 * @param argumentNames     custom argument display names
	 * @param argumentFormat    configured help argument format
	 * @param placeholderFormat placeholder format used in message templates
	 */
	public CommandantSyntaxFormatter(
			@NotNull CommandManager<C> manager,
			@Nullable Map<String, String> argumentNames,
			@Nullable HelpMessages.Format argumentFormat,
			@NotNull SerializerOptions.PlaceholderFormat placeholderFormat
	) {
		super(manager);
		this.argumentNames = argumentNames != null ? argumentNames : Map.of();
		this.argumentFormat = argumentFormat;
		this.placeholderFormat = placeholderFormat;
	}

	@Override
	protected @NotNull FormattingInstance createInstance() {
		return new CommandantFormattingInstance(argumentNames, argumentFormat, placeholderFormat);
	}

	private static final class CommandantFormattingInstance extends FormattingInstance {
		private final Map<String, String> argumentNames;
		private final HelpMessages.Format argumentFormat;
		private final SerializerOptions.PlaceholderFormat placeholderFormat;

		private CommandantFormattingInstance(
				@NotNull Map<String, String> argumentNames,
				@Nullable HelpMessages.Format argumentFormat,
				@NotNull SerializerOptions.PlaceholderFormat placeholderFormat
		) {
			this.argumentNames = argumentNames;
			this.argumentFormat = argumentFormat;
			this.placeholderFormat = placeholderFormat;
		}

		@Override
		public void appendRequired(@NotNull CommandComponent<?> argument) {
			super.appendName(format(argument, requiredTemplate()));
		}

		@Override
		public void appendOptional(@NotNull CommandComponent<?> argument) {
			super.appendName(format(argument, optionalTemplate()));
		}

		@Override
		public void appendAggregate(
				@NotNull CommandComponent<?> component,
				@NotNull AggregateParser<?, ?> parser
		) {
			String template = component.required() ? requiredTemplate() : optionalTemplate();
			Iterator<? extends CommandComponent<?>> innerComponents = parser.components().iterator();
			while (innerComponents.hasNext()) {
				CommandComponent<?> innerComponent = innerComponents.next();
				super.appendName(format(innerComponent, template));
				if (innerComponents.hasNext()) appendBlankSpace();
			}
		}

		private @NotNull String format(@NotNull CommandComponent<?> argument, @NotNull String template) {
			return template.replace(placeholderFormat.format("argument"), argumentName(argument));
		}

		private @NotNull String argumentName(@NotNull CommandComponent<?> argument) {
			return argumentNames.getOrDefault(argument.name(), argument.name());
		}

		private @NotNull String requiredTemplate() {
			if (argumentFormat == null || argumentFormat.getArgument() == null)
				return placeholderFormat.format("argument");

			return argumentFormat.getArgument();
		}

		private @NotNull String optionalTemplate() {
			if (argumentFormat == null || argumentFormat.getOptionalArgument() == null)
				return placeholderFormat.format("argument");

			return argumentFormat.getOptionalArgument();
		}
	}
}
