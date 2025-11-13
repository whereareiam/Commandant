package me.whereareiam.commandant.common;

import me.whereareiam.commandant.builder.HelpBuilder;
import me.whereareiam.commandant.builder.PaginationBuilder;
import me.whereareiam.commandant.model.message.HelpMessages;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of HelpBuilder.
 * Formats a list of commands with their arguments and descriptions,
 * optionally including pagination.
 * <p>
 * This builder is generic and works with any sender type.
 * <p>
 * Usage example:
 * <pre>{@code
 * HelpMessages config = ...; // from your config
 * Map<String, String> customArgNames = Map.of("page", "page number");
 * PaginationBuilder paginationBuilder = ...; // optional
 *
 * HelpBuilder<Actor> builder = new DefaultHelpBuilder<>(config, customArgNames, paginationBuilder, 10);
 *
 * Collection<Command<Actor>> commands = commandManager.commands();
 * String helpMessage = builder.build(commands, 1);
 * }</pre>
 *
 * @param <S> The sender type (e.g., Actor, DummyPlayer, CommandSender)
 */
public class DefaultHelpBuilder<S> implements HelpBuilder<S> {
	private final HelpMessages messages;
	private final Map<String, String> customArgumentNames;
	private final PaginationBuilder paginationBuilder;
	private final int itemsPerPage;

	/**
	 * Creates a new DefaultHelpBuilder with the given configuration.
	 *
	 * @param messages            The help message configuration
	 * @param customArgumentNames Map of argument names to custom display names (can be null or empty)
	 * @param paginationBuilder   The pagination builder (can be null to disable pagination)
	 * @param itemsPerPage        Number of commands to display per page
	 */
	public DefaultHelpBuilder(
			@NotNull HelpMessages messages,
			@Nullable Map<String, String> customArgumentNames,
			@Nullable PaginationBuilder paginationBuilder,
			int itemsPerPage
	) {
		this.messages = messages;
		this.customArgumentNames = customArgumentNames != null ? customArgumentNames : Map.of();
		this.paginationBuilder = paginationBuilder;
		this.itemsPerPage = itemsPerPage;
	}

	@Override
	@NotNull
	public String build(@NotNull Collection<Command<S>> commands, int page) {
		String message = String.join("\n", messages.getFormat());

		if (message.contains("{commands}"))
			message = buildCommandList(commands, message, page, itemsPerPage);

		if (message.contains("{pagination}") && paginationBuilder != null)
			message = paginationBuilder.build(message, commands.size(), page, itemsPerPage);
		else
			message = message.replace("{pagination}", "");

		return message;
	}

	/**
	 * Builds the command list section.
	 *
	 * @param commands     Collection of commands
	 * @param message      The message template
	 * @param page         Current page number
	 * @param itemsPerPage Number of items per page
	 * @return Message with {commands} placeholder replaced
	 */
	@NotNull
	private String buildCommandList(
			@NotNull Collection<Command<S>> commands,
			@NotNull String message,
			int page,
			int itemsPerPage
	) {
		if (commands.isEmpty())
			return message.replace("{commands}", messages.getNoCommands());

		long skip = (long) (page - 1) * itemsPerPage;

		List<String> commandDescriptions = commands.stream()
				.skip(skip)
				.limit(itemsPerPage)
				.map(this::formatCommand)
				.collect(Collectors.toList());

		String commandsString = String.join("\n", commandDescriptions);
		return message.replace("{commands}", commandsString);
	}

	/**
	 * Formats a single command entry.
	 *
	 * @param command The command to format
	 * @return Formatted command string
	 */
	@NotNull
	private String formatCommand(@NotNull Command<S> command) {
		String commandName = command.rootComponent().name();
		String arguments = formatCommandArguments(command.nonFlagArguments());
		String description = command.commandDescription().description().textDescription();

		return messages.getCommandFormat()
				.replace("{command}", commandName)
				.replace("{arguments}", arguments)
				.replace("{description}", description);
	}

	/**
	 * Formats command arguments according to their type (required/optional).
	 *
	 * @param arguments List of command components (arguments)
	 * @return Formatted arguments string
	 */
	@NotNull
	private String formatCommandArguments(@NotNull List<? extends CommandComponent<?>> arguments) {
		if (arguments.isEmpty())
			return "";

		HelpMessages.Format format = messages.getArgumentFormat();
		if (format == null)
			return ""; // No argument formatting configured

		return arguments.stream()
				.skip(1) // Skip the command name itself
				.map(argument -> formatSingleArgument(argument, format))
				.collect(Collectors.collectingAndThen(
						Collectors.joining(" "),
						formattedArguments -> formattedArguments.isEmpty() ? "" : " " + formattedArguments
				));
	}

	/**
	 * Formats a single argument based on its type.
	 *
	 * @param argument The argument component
	 * @param format   The format configuration
	 * @return Formatted argument string
	 */
	@NotNull
	private String formatSingleArgument(@NotNull CommandComponent<?> argument, @NotNull HelpMessages.Format format) {
		String argumentName = customArgumentNames.getOrDefault(argument.name(), argument.name());

		return switch (argument.type()) {
			case REQUIRED_VARIABLE -> format.getArgument().replace("{argument}", argumentName);
			case OPTIONAL_VARIABLE -> format.getOptionalArgument().replace("{argument}", argumentName);
			default -> argumentName;
		};
	}
}

