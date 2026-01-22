package me.whereareiam.commandant.common;

import me.whereareiam.commandant.CommandantKeys;
import me.whereareiam.commandant.builder.HelpBuilder;
import me.whereareiam.commandant.builder.PaginationBuilder;
import me.whereareiam.commandant.model.message.HelpMessages;
import me.whereareiam.keystone.model.SerializerOptions;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
	private final boolean sortAlphabetically;
	private final boolean dedupeByDefinitionId;
	private final SerializerOptions.PlaceholderFormat placeholderFormat;

	/**
	 * Creates a new DefaultHelpBuilder with the given configuration.
	 *
	 * @param messages            The help message configuration
	 * @param customArgumentNames Map of argument names to custom display names (can be null or empty)
	 * @param paginationBuilder   The pagination builder (can be null to disable pagination)
	 * @param itemsPerPage        Number of commands to display per page
	 * @param sortAlphabetically  Whether to sort commands alphabetically by name
	 */
	public DefaultHelpBuilder(
			@NotNull HelpMessages messages,
			@Nullable Map<String, String> customArgumentNames,
			@Nullable PaginationBuilder paginationBuilder,
			int itemsPerPage,
			boolean sortAlphabetically
	) {
		this(messages, customArgumentNames, paginationBuilder, itemsPerPage, sortAlphabetically, false,
				SerializerOptions.PlaceholderFormat.CURLY_BRACES);
	}

	public DefaultHelpBuilder(
			@NotNull HelpMessages messages,
			@Nullable Map<String, String> customArgumentNames,
			@Nullable PaginationBuilder paginationBuilder,
			int itemsPerPage,
			boolean sortAlphabetically,
			boolean dedupeByDefinitionId,
			@NotNull SerializerOptions.PlaceholderFormat placeholderFormat
	) {
		this.messages = messages;
		this.customArgumentNames = customArgumentNames != null ? customArgumentNames : Map.of();
		this.paginationBuilder = paginationBuilder;
		this.itemsPerPage = itemsPerPage;
		this.sortAlphabetically = sortAlphabetically;
		this.dedupeByDefinitionId = dedupeByDefinitionId;
		this.placeholderFormat = placeholderFormat;
	}

	@Override
	@NotNull
	public String build(@NotNull Collection<Command<S>> commands, int page) {
		String message = String.join("\n", messages.getFormat());

		if (message.contains(token("commands")))
			message = buildCommandList(commands, message, page, itemsPerPage);

		if (message.contains(token("pagination")) && paginationBuilder != null)
			message = paginationBuilder.build(message, commands.size(), page, itemsPerPage);
		else
			message = replaceToken(message, "pagination", "");

		return message;
	}

	/**
	 * Builds the command list section.
	 *
	 * @param commands     Collection of commands
	 * @param message      The message template
	 * @param page         Current page number
	 * @param itemsPerPage Number of items per page
	 * @return Message with <commands> placeholder replaced
	 */
	@NotNull
	private String buildCommandList(
			@NotNull Collection<Command<S>> commands,
			@NotNull String message,
			int page,
			int itemsPerPage
	) {
		if (commands.isEmpty())
			return replaceToken(message, "commands", messages.getNoCommands());

		Collection<Command<S>> resolvedCommands = dedupeByDefinitionId
				? dedupeCommands(commands)
				: commands;

		long skip = (long) (page - 1) * itemsPerPage;

		var commandStream = resolvedCommands.stream();
		if (sortAlphabetically) {
			commandStream = commandStream.sorted((c1, c2) ->
					buildCommandKey(c1).compareToIgnoreCase(buildCommandKey(c2)));
		}

		List<String> commandDescriptions = commandStream
				.skip(skip)
				.limit(itemsPerPage)
				.map(this::formatCommand)
				.collect(Collectors.toList());

		String commandsString = String.join("\n", commandDescriptions);
		return replaceToken(message, "commands", commandsString);
	}

	@NotNull
	private Collection<Command<S>> dedupeCommands(@NotNull Collection<Command<S>> commands) {
		List<Command<S>> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (Command<S> command : commands) {
			String definitionId = command.commandMeta()
					.optional(CommandantKeys.DEFINITION_ID)
					.orElse(null);
			if (definitionId == null) {
				result.add(command);
				continue;
			}

			if (seen.add(definitionId))
				result.add(command);
		}

		return result;
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

		return replaceToken(
				replaceToken(
						replaceToken(messages.getCommandFormat(), "command", commandName),
						"arguments",
						arguments
				),
				"description",
				description
		);
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
		CommandComponent.ComponentType type = argument.type();
		boolean variable = type == CommandComponent.ComponentType.REQUIRED_VARIABLE
				|| type == CommandComponent.ComponentType.OPTIONAL_VARIABLE;

		String argumentName = variable
				? customArgumentNames.getOrDefault(argument.name(), argument.name())
				: argument.name();

		return switch (type) {
			case REQUIRED_VARIABLE -> replaceToken(format.getArgument(), "argument", argumentName);
			case OPTIONAL_VARIABLE -> replaceToken(format.getOptionalArgument(), "argument", argumentName);
			default -> argumentName;
		};
	}

	@NotNull
	private String token(@NotNull String placeholder) {
		return placeholderFormat.format(placeholder);
	}

	@NotNull
	private String replaceToken(@NotNull String message, @NotNull String placeholder, @NotNull String value) {
		return message.replace(token(placeholder), value);
	}

	@NotNull
	private String buildCommandKey(@NotNull Command<S> command) {
		return command.nonFlagArguments().stream()
				.map(CommandComponent::name)
				.collect(Collectors.joining(" "));
	}
}

