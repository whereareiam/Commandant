package me.whereareiam.commandant.common.registration;

import io.leangen.geantyref.TypeToken;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.CommandRegistrar;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation that bridges Cloud annotation parsing with {@link CommandDefinitionRegistration}.
 *
 * @param <S> sender type
 */
public final class AnnotationCommandRegistrar<S> implements CommandRegistrar<S> {
	private final CommandDefinitionRegistration<S> registration;
	private final AnnotationParser<S> annotationParser;
	private final Function<String, CommandDefinition> definitionLookup;

	public AnnotationCommandRegistrar(
			@NotNull CommandDefinitionRegistration<S> registration,
			@NotNull Class<S> senderType,
			@NotNull Function<String, CommandDefinition> definitionLookup
	) {
		this.registration = Objects.requireNonNull(registration, "registration");
		this.definitionLookup = Objects.requireNonNull(definitionLookup, "definitionLookup");

		RecordingCommandManager<S> recordingManager = new RecordingCommandManager<>();
		this.annotationParser = new AnnotationParser<>(recordingManager, senderType);
		this.annotationParser.registerBuilderModifier(
				me.whereareiam.commandant.annotation.Definition.class,
				(annotation, builder) -> builder.meta(CommandDefinitionRegistration.DEFINITION_ID_KEY, annotation.value())
		);
	}

	@Override
	public void register(@NotNull Object... containers) {
		List<Command<S>> parsedCommands = new ArrayList<>(this.annotationParser.parse(containers));
		CommandManager<S> commandManager = registration.getCommandManager();
		String rootCommand = registration.getRootCommand();

		for (Command<S> parsedCommand : parsedCommands) {
			Optional<String> definitionId = extractDefinitionId(parsedCommand);
			if (definitionId.isEmpty()) continue;

			CommandDefinition definition = definitionLookup.apply(definitionId.get());
			if (!shouldRegister(definition)) continue;

			// Create a map of argument names to parsed components for lookup
			Map<String, CommandComponent<S>> parsedComponentsByName = createComponentMap(parsedCommand);

			// Parse arguments from definition usage
			List<ArgumentToken> argumentTokens = parseArguments(definition.getUsage());

			// Register commands based on definition structure
			if (isSubcommand(rootCommand, definition)) {
				registerSubcommandsFromDefinition(
						commandManager,
						rootCommand,
						definition,
						definitionId.get(),
						argumentTokens,
						parsedComponentsByName,
						parsedCommand
				);
				continue;
			}

			registerStandaloneCommandFromDefinition(
					commandManager,
					definition,
					definitionId.get(),
					argumentTokens,
					parsedComponentsByName,
					parsedCommand
			);
		}
	}

	private Optional<String> extractDefinitionId(Command<S> command) {
		return command.commandMeta().optional(CommandDefinitionRegistration.DEFINITION_ID_KEY);
	}

	private boolean shouldRegister(CommandDefinition definition) {
		return definition != null && definition.isEnabled();
	}

	private boolean isSubcommand(String rootCommand, CommandDefinition definition) {
		return rootCommand != null &&
				definition.getUsage() != null &&
				definition.getUsage().contains("{command}");
	}

	private void registerSubcommandsFromDefinition(
			CommandManager<S> commandManager,
			String rootCommand,
			CommandDefinition definition,
			String definitionId,
			List<ArgumentToken> argumentTokens,
			Map<String, CommandComponent<S>> parsedComponentsByName,
			Command<S> parsedCommand
	) {
		List<String> aliases = sanitizeAliases(definition.getAliases());
		if (aliases.isEmpty()) {
			throw new IllegalArgumentException("Command must define at least one alias");
		}

		// Register a command for each alias
		for (String alias : aliases) {
			Command.Builder<S> builder = commandManager.commandBuilder(rootCommand);

			// Split multi-word aliases (e.g., "database upload" -> ["database", "upload"])
			for (String literal : splitAlias(alias))
				builder = builder.literal(literal);

			// Add arguments from definition usage
			for (ArgumentToken token : argumentTokens) {
				CommandComponent<S> parsedComponent = parsedComponentsByName.get(token.name());
				if (parsedComponent != null) builder = addComponent(builder, parsedComponent);
			}

			builder = applyMetadata(builder, definitionId, definition);
			builder = builder.handler(ctx -> parsedCommand.commandExecutionHandler().executeFuture(ctx));

			commandManager.command(builder);
		}
	}

	private void registerStandaloneCommandFromDefinition(
			CommandManager<S> commandManager,
			CommandDefinition definition,
			String definitionId,
			List<ArgumentToken> argumentTokens,
			Map<String, CommandComponent<S>> parsedComponentsByName,
			Command<S> parsedCommand
	) {
		List<String> aliases = sanitizeAliases(definition.getAliases());
		if (aliases.isEmpty()) throw new IllegalArgumentException("Command must define at least one alias");

		String primary = aliases.get(0);
		String[] secondary = aliases.stream()
				.skip(1)
				.filter(alias -> !alias.contains(" "))
				.toArray(String[]::new);

		Command.Builder<S> builder = commandManager.commandBuilder(primary, secondary);

		// Add arguments from definition usage
		for (ArgumentToken token : argumentTokens) {
			CommandComponent<S> parsedComponent = parsedComponentsByName.get(token.name());
			if (parsedComponent != null) builder = addComponent(builder, parsedComponent);
		}

		builder = applyMetadata(builder, definitionId, definition);
		builder = builder.handler(ctx -> parsedCommand.commandExecutionHandler().executeFuture(ctx));

		commandManager.command(builder);

		// Register multi-word aliases as separate commands
		aliases.stream()
				.skip(1)
				.filter(alias -> alias.contains(" "))
				.forEach(alias -> {
					Command.Builder<S> aliasBuilder = commandManager.commandBuilder(primary);
					for (String literal : splitAlias(alias)) {
						aliasBuilder = aliasBuilder.literal(literal);
					}

					// Add arguments
					for (ArgumentToken token : argumentTokens) {
						CommandComponent<S> parsedComponent = parsedComponentsByName.get(token.name());
						if (parsedComponent != null) {
							aliasBuilder = addComponent(aliasBuilder, parsedComponent);
						}
					}

					aliasBuilder = applyMetadata(aliasBuilder, definitionId, definition);
					aliasBuilder = aliasBuilder.handler(ctx -> parsedCommand.commandExecutionHandler().executeFuture(ctx));

					commandManager.command(aliasBuilder);
				});
	}

	private List<String> splitAlias(String alias) {
		return List.of(alias.trim().split("\\s+"));
	}

	private Map<String, CommandComponent<S>> createComponentMap(Command<S> parsedCommand) {
		Map<String, CommandComponent<S>> map = new HashMap<>();
		for (CommandComponent<S> component : parsedCommand.components()) {
			// Skip literal components (they're part of the command structure, not arguments)
			if (component.type() == CommandComponent.ComponentType.LITERAL) {
				continue;
			}
			map.put(component.name(), component);
		}
		return map;
	}

	private List<ArgumentToken> parseArguments(@Nullable String usage) {
		if (usage == null || usage.isBlank())
			return Collections.emptyList();

		List<ArgumentToken> tokens = new ArrayList<>();
		for (String token : usage.split("\\s+")) {
			if (token.isBlank() || isPlaceholder(token)) continue;

			boolean required = token.startsWith("<") && token.endsWith(">");
			boolean optional = token.startsWith("[") && token.endsWith("]");

			if (!required && !optional) continue;

			String cleaned = token.substring(1, token.length() - 1).trim();
			boolean greedy = cleaned.endsWith("...");
			if (greedy) cleaned = cleaned.substring(0, cleaned.length() - 3);

			if (!cleaned.isEmpty()) tokens.add(new ArgumentToken(cleaned, required, greedy));
		}

		return tokens;
	}

	private boolean isPlaceholder(String token) {
		return token.startsWith("{") && token.endsWith("}");
	}


	private List<String> sanitizeAliases(List<String> aliases) {
		if (aliases == null || aliases.isEmpty()) {
			return Collections.emptyList();
		}

		return aliases.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(alias -> !alias.isEmpty())
				.collect(Collectors.toList());
	}

	private Command.Builder<S> applyMetadata(
			Command.Builder<S> builder,
			String definitionId,
			CommandDefinition definition
	) {
		builder = builder.meta(CommandDefinitionRegistration.DEFINITION_ID_KEY, definitionId);

		String description = definition.getDescription();
		if (description != null && !description.isBlank())
			builder = builder.commandDescription(Description.of(description));

		String permission = definition.getPermission();
		if (permission != null && !permission.isBlank())
			builder = builder.permission(Permission.of(permission));

		return builder;
	}

	@Override
	public void setRootCommand(@NotNull String root) {
		this.registration.setRootCommand(root);
	}

	@Override
	public @NotNull Optional<CommandDefinition> resolveDefinition(@NotNull Command<S> command) {
		return command.commandMeta().optional(CommandDefinitionRegistration.DEFINITION_ID_KEY).map(definitionLookup);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Command.Builder<S> addComponent(Command.Builder<S> builder, CommandComponent<S> component) {
		CommandComponent.Builder componentBuilder = createComponentBuilder(component);

		if (component.required())
			return builder.required(componentBuilder);

		return builder.optional(componentBuilder);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private CommandComponent.Builder createComponentBuilder(CommandComponent<S> component) {
		ArgumentParser rawParser = component.parser();
		TypeToken rawValueType = component.valueType();
		ParserDescriptor parserDescriptor = ParserDescriptor.of(rawParser, rawValueType);

		CommandComponent.Builder componentBuilder = CommandComponent.builder()
				.name(component.name())
				.parser(parserDescriptor)
				.description(component.description());

		if (component.hasDefaultValue())
			componentBuilder = componentBuilder.defaultValue(component.defaultValue());

		return componentBuilder;
	}

	private static final class RecordingCommandManager<S> extends CommandManager<S> {
		RecordingCommandManager() {
			super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
		}

		@Override
		public boolean hasPermission(@NotNull S sender, @NotNull String permission) {
			return true;
		}
	}

	private record ArgumentToken(String name, boolean required, boolean greedy) {}
}

