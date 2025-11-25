package me.whereareiam.commandant.common.registration;

import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Minimal copy of CommandDefinitionRegistration from Commandant common module so that
 * Intercept can use the public Commandant API without depending on the common artifact.
 *
 * @param <S> sender type
 */
public class CommandDefinitionRegistration<S> {
	public static final CloudKey<String> DEFINITION_ID_KEY = CloudKey.of("commandant:definitionId", String.class);
	private final CommandManager<S> commandManager;
	private String rootCommand;

	public CommandDefinitionRegistration(
			@NotNull CommandManager<S> commandManager
	) {
		this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
	}

	public void register(
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		register(null, definition, handler);
	}

	public void register(
			@Nullable String definitionId,
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(handler, "handler");

		if (!definition.isEnabled()) {
			return;
		}

		List<String> aliases = sanitizeAliases(definition.getAliases());
		if (aliases.isEmpty()) {
			throw new IllegalArgumentException("Command must define at least one alias");
		}

		List<ArgumentToken> arguments = parseArguments(definition.getUsage());

		if (shouldRegisterAsSubcommand(definition)) {
			registerSubcommand(definitionId, definition, handler, aliases, arguments);
			return;
		}

		registerRoot(definitionId, definition, handler, aliases, arguments);
	}

	private void registerRoot(
			@Nullable String definitionId,
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler,
			@NotNull List<String> aliases,
			@NotNull List<ArgumentToken> arguments
	) {
		String primary = aliases.get(0);
		String[] secondary = aliases.stream()
				.skip(1)
				.filter(alias -> !alias.contains(" "))
				.toArray(String[]::new);

		Command.Builder<S> builder = commandManager.commandBuilder(primary, secondary);
		commandManager.command(applyMetadata(builder, definition, handler, arguments, definitionId));

		aliases.stream()
				.skip(1)
				.filter(alias -> alias.contains(" "))
				.forEach(alias -> {
					Command.Builder<S> aliasBuilder = commandManager.commandBuilder(primary);
					for (String literal : splitAlias(alias)) {
						aliasBuilder = aliasBuilder.literal(literal);
					}
					commandManager.command(applyMetadata(aliasBuilder, definition, handler, arguments, definitionId));
				});
	}

	private void registerSubcommand(
			@Nullable String definitionId,
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler,
			@NotNull List<String> aliases,
			@NotNull List<ArgumentToken> arguments
	) {
		for (String alias : aliases) {
			Command.Builder<S> builder = commandManager.commandBuilder(rootCommand);
			for (String literal : splitAlias(alias)) {
				builder = builder.literal(literal);
			}

			commandManager.command(applyMetadata(builder, definition, handler, arguments, definitionId));
		}
	}

	private Command.Builder<S> applyMetadata(
			Command.Builder<S> builder,
			CommandDefinition definition,
			Consumer<CommandContext<S>> handler,
			List<ArgumentToken> arguments,
			@Nullable String definitionId
	) {
		if (definitionId != null) {
			builder = builder.meta(DEFINITION_ID_KEY, definitionId);
		}
		String description = definition.getDescription();
		if (description != null && !description.isBlank()) {
			builder = builder.commandDescription(Description.of(description));
		}

		String permission = definition.getPermission();
		if (permission != null && !permission.isBlank()) {
			builder = builder.permission(Permission.of(permission));
		}

		Map<String, String> argumentDescriptions = defaultMap(definition.getArguments());
		for (ArgumentToken token : arguments) {
			builder = appendArgument(builder, token, argumentDescriptions);
		}

		return builder.handler(handler::accept);
	}

	private Command.Builder<S> appendArgument(
			Command.Builder<S> builder,
			ArgumentToken token,
			Map<String, String> argumentDescriptions
	) {
		CommandComponent.Builder<S, String> componentBuilder = CommandComponent.<S, String>builder()
				.name(token.name())
				.parser(token.greedy() ? StringParser.greedyStringParser() : StringParser.stringParser());

		String description = argumentDescriptions.get(token.name());
		if (description != null && !description.isBlank()) {
			componentBuilder = componentBuilder.description(Description.of(description));
		}

		if (token.required()) {
			return builder.required(componentBuilder);
		}
		return builder.optional(componentBuilder);
	}

	private List<String> splitAlias(String alias) {
		return List.of(alias.trim().split("\\s+"));
	}

	private List<String> sanitizeAliases(List<String> aliases) {
		if (aliases == null || aliases.isEmpty()) {
			return List.of();
		}

		return aliases.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(alias -> !alias.isEmpty())
				.collect(Collectors.toList());
	}

	private List<ArgumentToken> parseArguments(@Nullable String usage) {
		if (usage == null || usage.isBlank()) {
			return Collections.emptyList();
		}

		List<ArgumentToken> tokens = new ArrayList<>();
		for (String token : usage.split("\\s+")) {
			if (token.isBlank() || isPlaceholder(token)) {
				continue;
			}

			boolean required = token.startsWith("<") && token.endsWith(">");
			boolean optional = token.startsWith("[") && token.endsWith("]");

			if (!required && !optional) {
				continue;
			}

			String cleaned = token.substring(1, token.length() - 1).trim();
			boolean greedy = cleaned.endsWith("...");
			if (greedy) {
				cleaned = cleaned.substring(0, cleaned.length() - 3);
			}

			if (!cleaned.isEmpty()) {
				tokens.add(new ArgumentToken(cleaned, required, greedy));
			}
		}
		return tokens;
	}

	private boolean isPlaceholder(String token) {
		return token.startsWith("{") && token.endsWith("}");
	}

	private Map<String, String> defaultMap(Map<String, String> map) {
		return map == null ? Collections.emptyMap() : map;
	}

	public void setRootCommand(@Nullable String rootCommand) {
		this.rootCommand = rootCommand;
	}

	@Nullable
	public String getRootCommand() {
		return rootCommand;
	}

	private record ArgumentToken(String name, boolean required, boolean greedy) {
	}

	private boolean shouldRegisterAsSubcommand(@NotNull CommandDefinition definition) {
		return rootCommand != null && usageContainsCommandPlaceholder(definition.getUsage());
	}

	private boolean usageContainsCommandPlaceholder(@Nullable String usage) {
		return usage != null && usage.contains("{command}");
	}
}

