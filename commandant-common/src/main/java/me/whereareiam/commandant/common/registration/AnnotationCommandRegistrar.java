package me.whereareiam.commandant.common.registration;

import io.leangen.geantyref.TypeToken;
import me.whereareiam.commandant.annotation.Definition;
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
				Definition.class,
				(annotation, builder) -> builder.meta(CommandDefinitionRegistration.DEFINITION_ID_KEY, annotation.value())
		);
	}

	@Override
	public void register(@NotNull Object... containers) {
		CommandManager<S> manager = registration.getCommandManager();
		String root = registration.getRootCommand();

		for (Command<S> parsed : this.annotationParser.parse(containers)) {
			String definitionId = definitionId(parsed);
			if (definitionId == null) continue;

			CommandDefinition definition = definitionLookup.apply(definitionId);
			if (definition == null || !definition.isEnabled()) continue;

			Map<String, CommandComponent<S>> componentsByName = indexArgumentComponents(parsed);
			List<ArgumentToken> tokens = parseArguments(definition.getUsage());

			if (isSubcommand(root, definition)) {
				registerFromDefinition(manager, root, definition, definitionId, tokens, componentsByName, parsed);
				continue;
			}

			registerFromDefinition(manager, null, definition, definitionId, tokens, componentsByName, parsed);
		}
	}

	@Override
	public void setRootCommand(@NotNull String root) {
		this.registration.setRootCommand(root);
	}

	@Override
	public @NotNull Optional<CommandDefinition> resolveDefinition(@NotNull Command<S> command) {
		return command.commandMeta()
				.optional(CommandDefinitionRegistration.DEFINITION_ID_KEY)
				.map(definitionLookup);
	}

	private @Nullable String definitionId(Command<S> parsed) {
		return parsed.commandMeta()
				.optional(CommandDefinitionRegistration.DEFINITION_ID_KEY)
				.orElse(null);
	}

	private boolean isSubcommand(@Nullable String rootCommand, @NotNull CommandDefinition definition) {
		String usage = definition.getUsage();
		return rootCommand != null && usage != null && usage.contains("{command}");
	}

	private void registerFromDefinition(
			CommandManager<S> manager,
			@Nullable String rootCommand,
			CommandDefinition definition,
			String definitionId,
			List<ArgumentToken> argumentTokens,
			Map<String, CommandComponent<S>> componentsByName,
			Command<S> parsedCommand
	) {
		List<String> aliases = requireAliases(definition);

		AliasGroups groups = splitAliases(aliases);

		registerSingleWordAliases(manager, rootCommand, groups.singleWord(), definition, definitionId, argumentTokens, componentsByName, parsedCommand);
		registerMultiWordAliases(manager, rootCommand, groups.multiWord(), definition, definitionId, argumentTokens, componentsByName, parsedCommand);
	}

	private List<String> requireAliases(CommandDefinition definition) {
		List<String> aliases = sanitizeAliases(definition.getAliases());
		if (!aliases.isEmpty()) return aliases;
		throw new IllegalArgumentException("Command must define at least one alias");
	}

	private void registerSingleWordAliases(
			CommandManager<S> manager,
			@Nullable String rootCommand,
			List<String> singleWordAliases,
			CommandDefinition definition,
			String definitionId,
			List<ArgumentToken> argumentTokens,
			Map<String, CommandComponent<S>> componentsByName,
			Command<S> parsedCommand
	) {
		if (singleWordAliases.isEmpty()) return;

		String primary = singleWordAliases.get(0);
		String[] secondary = singleWordAliases.stream().skip(1).toArray(String[]::new);

		Command.Builder<S> builder = (rootCommand != null)
				? manager.commandBuilder(rootCommand).literal(primary, secondary)
				: manager.commandBuilder(primary, secondary);

		registerBuilt(manager, builder, definition, definitionId, argumentTokens, componentsByName, parsedCommand);
	}

	/**
	 * Multi-word aliases are grouped by their prefix (all tokens except the last),
	 * so we can register:
	 * <p>
	 * - "database download"
	 * - "database d"
	 * <p>
	 * as a single command path: "database" + ("download" | "d")
	 * <p>
	 * Works for deeper paths too:
	 * - "db user add"
	 * - "db user a"
	 * <p>
	 * becomes: "db" "user" + ("add" | "a")
	 */
	private void registerMultiWordAliases(
			CommandManager<S> manager,
			@Nullable String rootCommand,
			List<String> multiWordAliases,
			CommandDefinition definition,
			String definitionId,
			List<ArgumentToken> argumentTokens,
			Map<String, CommandComponent<S>> componentsByName,
			Command<S> parsedCommand
	) {
		if (multiWordAliases.isEmpty()) return;

		Map<List<String>, List<String>> grouped = groupByPrefix(multiWordAliases);

		for (Map.Entry<List<String>, List<String>> entry : grouped.entrySet()) {
			List<String> prefix = entry.getKey();
			List<String> suffixes = distinct(entry.getValue());
			if (prefix.isEmpty() || suffixes.isEmpty()) continue;

			Command.Builder<S> builder = baseBuilder(manager, rootCommand, prefix);
			builder = builder.literal(suffixes.get(0), suffixes.stream().skip(1).toArray(String[]::new));

			registerBuilt(manager, builder, definition, definitionId, argumentTokens, componentsByName, parsedCommand);
		}
	}

	private Command.Builder<S> baseBuilder(CommandManager<S> manager, @Nullable String rootCommand, List<String> prefixParts) {
		if (rootCommand != null) {
			Command.Builder<S> b = manager.commandBuilder(rootCommand);
			for (String p : prefixParts) b = b.literal(p);
			return b;
		}

		Command.Builder<S> b = manager.commandBuilder(prefixParts.get(0));
		for (int i = 1; i < prefixParts.size(); i++) b = b.literal(prefixParts.get(i));
		return b;
	}

	private List<String> distinct(List<String> in) {
		if (in.isEmpty()) return in;
		return new ArrayList<>(new LinkedHashSet<>(in));
	}

	private void registerBuilt(
			CommandManager<S> commandManager,
			Command.Builder<S> builder,
			CommandDefinition definition,
			String definitionId,
			List<ArgumentToken> argumentTokens,
			Map<String, CommandComponent<S>> componentsByName,
			Command<S> parsedCommand
	) {
		builder = addArguments(builder, argumentTokens, componentsByName);
		builder = applyMetadata(builder, definitionId, definition);
		builder = builder.handler(ctx -> parsedCommand.commandExecutionHandler().executeFuture(ctx));
		commandManager.command(builder);
	}

	private Command.Builder<S> addArguments(
			Command.Builder<S> builder,
			List<ArgumentToken> argumentTokens,
			Map<String, CommandComponent<S>> componentsByName
	) {
		for (ArgumentToken token : argumentTokens) {
			CommandComponent<S> component = componentsByName.get(token.name());
			if (component == null) continue;
			builder = addComponent(builder, component);
		}
		return builder;
	}

	private AliasGroups splitAliases(List<String> aliases) {
		List<String> singleWord = new ArrayList<>();
		List<String> multiWord = new ArrayList<>();

		for (String alias : aliases) {
			if (alias.contains(" ")) multiWord.add(alias);
			else singleWord.add(alias);
		}

		return new AliasGroups(singleWord, multiWord);
	}

	private List<String> splitAlias(String alias) {
		return List.of(alias.trim().split("\\s+"));
	}

	/**
	 * Group multi-word aliases by their prefix (all tokens except the last token),
	 * and collect the last token as a suffix alias.
	 */
	private Map<List<String>, List<String>> groupByPrefix(List<String> multiWordAliases) {
		Map<List<String>, List<String>> grouped = new LinkedHashMap<>();

		for (String alias : multiWordAliases) {
			List<String> parts = splitAlias(alias);
			if (parts.size() < 2) continue;

			List<String> prefix = List.copyOf(parts.subList(0, parts.size() - 1));
			String suffix = parts.get(parts.size() - 1);
			if (suffix.isBlank()) continue;

			grouped.computeIfAbsent(prefix, k -> new ArrayList<>()).add(suffix);
		}

		return grouped;
	}

	private Map<String, CommandComponent<S>> indexArgumentComponents(Command<S> parsedCommand) {
		Map<String, CommandComponent<S>> map = new HashMap<>();

		for (CommandComponent<S> component : parsedCommand.components()) {
			if (component.type() == CommandComponent.ComponentType.LITERAL) continue;
			map.put(component.name(), component);
		}

		return map;
	}

	private List<ArgumentToken> parseArguments(@Nullable String usage) {
		if (usage == null || usage.isBlank()) return Collections.emptyList();

		List<ArgumentToken> tokens = new ArrayList<>();

		for (String token : usage.split("\\s+")) {
			if (token.isBlank()) continue;
			if (token.startsWith("{") && token.endsWith("}")) continue;

			boolean required = token.startsWith("<") && token.endsWith(">");
			boolean optional = token.startsWith("[") && token.endsWith("]");
			if (!required && !optional) continue;

			String cleaned = token.substring(1, token.length() - 1).trim();

			boolean greedy = cleaned.endsWith("...");
			if (greedy) cleaned = cleaned.substring(0, cleaned.length() - 3);

			if (cleaned.isEmpty()) continue;
			tokens.add(new ArgumentToken(cleaned, required, greedy));
		}

		return tokens;
	}

	private List<String> sanitizeAliases(@Nullable List<String> aliases) {
		if (aliases == null || aliases.isEmpty()) return Collections.emptyList();

		LinkedHashSet<String> out = new LinkedHashSet<>();
		for (String alias : aliases) {
			if (alias == null) continue;
			String trimmed = alias.trim();
			if (trimmed.isEmpty()) continue;
			out.add(trimmed);
		}
		return new ArrayList<>(out);
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

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Command.Builder<S> addComponent(Command.Builder<S> builder, CommandComponent<S> component) {
		CommandComponent.Builder componentBuilder = createComponentBuilder(component);
		if (component.required()) return builder.required(componentBuilder);
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

	private record AliasGroups(List<String> singleWord, List<String> multiWord) {}
}
