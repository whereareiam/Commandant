package me.whereareiam.commandant.common;

import me.whereareiam.commandant.CommandRegistrar;
import me.whereareiam.commandant.model.CommandDefinition;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.CommandExecutionHandler;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.processors.cooldown.*;
import org.incendo.cloud.processors.cooldown.listener.ScheduledCleanupCreationListener;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of CommandRegistrar for programmatically registering commands with Cloud CommandManager.
 *
 * @param <S> The sender type (e.g., DummyPlayer, CommandSender, Audience)
 */
public class DefaultCommandRegistrar<S> implements CommandRegistrar<S> {
	private final CommandManager<S> commandManager;
	private final Function<S, UUID> uuidExtractor;
	private final Function<String, SuggestionProvider<S>> suggestionResolver;
	private String rootCommand;

	/**
	 * Creates a new CommandRegistrar.
	 *
	 * @param commandManager The command manager to register commands with
	 * @param uuidExtractor  Function to extract UUID from sender for cooldown tracking
	 */
	private DefaultCommandRegistrar(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor,
			@NotNull Function<String, SuggestionProvider<S>> suggestionResolver
	) {
		this.commandManager = commandManager;
		this.uuidExtractor = uuidExtractor;
		this.suggestionResolver = suggestionResolver;

		CooldownManager<S> cooldownManager = createCooldownManager();
		commandManager.registerCommandPostProcessor(cooldownManager.createPostprocessor());
	}

	/**
	 * Creates a new CommandRegistrar instance.
	 *
	 * @param commandManager The command manager to register commands with
	 * @param uuidExtractor  Function to extract UUID from sender for cooldown tracking
	 * @param <S>            The sender type
	 * @return A new CommandRegistrar instance
	 */
	@NotNull
	public static <S> CommandRegistrar<S> create(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor
	) {
		return create(commandManager, uuidExtractor, name -> null);
	}

	@NotNull
	public static <S> CommandRegistrar<S> create(
			@NotNull CommandManager<S> commandManager,
			@NotNull Function<S, UUID> uuidExtractor,
			@NotNull Function<String, SuggestionProvider<S>> suggestionResolver
	) {
		return new DefaultCommandRegistrar<>(commandManager, uuidExtractor, suggestionResolver);
	}

	@Override
	public void registerCommand(
			@NotNull CommandDefinition definition,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		if (!definition.isEnabled()) return;

		List<String> aliases = definition.getAliases();
		if (aliases == null || aliases.isEmpty()) throw new IllegalArgumentException("Command aliases cannot be empty");

		// Check if usage contains {command} to determine if it should be a subcommand
		boolean isSubcommand = rootCommand != null
				&& definition.getUsage() != null
				&& definition.getUsage().contains("{command}");

		if (isSubcommand) {
			registerSubcommand(definition, aliases, handler);
			return;
		}

		registerRootCommand(definition, aliases, handler);
	}

	private void registerSubcommand(
			@NotNull CommandDefinition definition,
			@NotNull List<String> aliases,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		// Split aliases into parts (handle multi-word aliases like "database upload")
		// Group aliases by number of parts, as aliases with different lengths need separate registration
		Map<Integer, List<List<String>>> aliasesByLength = aliases.stream()
				.map(alias -> List.of(alias.split("\\s+")))
				.collect(Collectors.groupingBy(List::size));

		// Register each group of aliases (with the same number of parts) separately
		// This handles cases like ["database upload", "upload"] where lengths differ
		for (Map.Entry<Integer, List<List<String>>> entry : aliasesByLength.entrySet()) {
			List<List<String>> aliasParts = entry.getValue();

			// Build the command chain by chaining literals
			// Group aliases by position to create alternatives at each level
			int numParts = entry.getKey();
			Command.Builder<S> builder = commandManager.commandBuilder(rootCommand);

			for (int i = 0; i < numParts; i++) {
				final int position = i;
				List<String> alternatives = aliasParts.stream()
						.map(parts -> parts.get(position))
						.distinct()
						.toList();

				if (!alternatives.isEmpty()) {
					String mainLiteral = alternatives.get(0);
					String[] remainingLiterals = alternatives.size() > 1
							? alternatives.subList(1, alternatives.size()).toArray(new String[0])
							: new String[0];
					builder = builder.literal(mainLiteral, remainingLiterals);
				}
			}

			builder = builder.commandDescription(Description.of(definition.getDescription() != null
					? definition.getDescription()
					: ""
			));

			applyCommandArguments(builder, definition);
			applyCommandProperties(builder, definition);
			registerBuiltCommand(builder, handler);
		}
	}

	private void registerRootCommand(
			@NotNull CommandDefinition definition,
			@NotNull List<String> aliases,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		// Use first alias as main name, rest as aliases
		String mainAlias = aliases.get(0);
		String[] remainingAliases = aliases.size() > 1
				? aliases.subList(1, aliases.size()).toArray(new String[0])
				: new String[0];

		Command.Builder<S> builder = commandManager.commandBuilder(mainAlias, remainingAliases)
				.commandDescription(Description.of(definition.getDescription() != null ? definition.getDescription() : ""));

		applyCommandArguments(builder, definition);
		applyCommandProperties(builder, definition);
		registerBuiltCommand(builder, handler);
	}

	private void applyCommandArguments(
			@NotNull Command.Builder<S> builder,
			@NotNull CommandDefinition definition
	) {
		String usage = definition.getUsage();
		if (usage == null || usage.isBlank()) return;

		List<UsageArgument> usageArguments = parseUsageArguments(usage);
		if (usageArguments.isEmpty()) return;

		Map<String, String> descriptions = definition.getArguments();
		if (descriptions == null) {
			descriptions = new HashMap<>();
			definition.setArguments(descriptions);
		}

		for (UsageArgument usageArgument : usageArguments) {
			descriptions.putIfAbsent(usageArgument.name(), usageArgument.name());

			CommandComponent.Builder<S, ?> componentBuilder = usageArgument.greedy()
					? CommandComponent.builder(usageArgument.name(), StringParser.greedyStringParser())
					: CommandComponent.builder(usageArgument.name(), StringParser.stringParser());

			String description = descriptions != null ? descriptions.get(usageArgument.name()) : null;
			if (description != null && !description.isBlank())
				componentBuilder.description(Description.of(description));

			SuggestionProvider<S> suggestions = suggestionResolver.apply(usageArgument.name());
			if (suggestions != null)
				componentBuilder.suggestionProvider(suggestions);

			if (usageArgument.required())
				builder.required(componentBuilder);
			else
				builder.optional(componentBuilder);
		}
	}

	private List<UsageArgument> parseUsageArguments(@NotNull String usage) {
		String[] tokens = usage.split("\\s+");
		List<UsageArgument> arguments = new ArrayList<>();

		for (String token : tokens) {
			if (token.isBlank()) continue;

			boolean required = token.startsWith("<") && token.endsWith(">");
			boolean optional = token.startsWith("[") && token.endsWith("]");
			if (!required && !optional) continue;

			String name = token.substring(1, token.length() - 1);
			boolean greedy = name.endsWith("...");
			if (greedy) name = name.substring(0, name.length() - 3);
			if (name.isEmpty()) continue;

			arguments.add(new UsageArgument(name, required, greedy));
		}

		return arguments;
	}

	private void applyCommandProperties(
			@NotNull Command.Builder<S> builder,
			@NotNull CommandDefinition definition
	) {
		// Add permission if specified
		if (definition.getPermission() != null && !definition.getPermission().isEmpty())
			builder.permission(definition.getPermission());

		// Add cooldown if enabled
		if (definition.getCooldown() != null && definition.getCooldown().isEnabled()) {
			Cooldown<S> cooldown = Cooldown.of(
					DurationFunction.constant(Duration.ofSeconds(definition.getCooldown().getDuration())),
					CooldownGroup.named(definition.getCooldown().getGroup())
			);
			builder.apply(cooldown);
		}
	}

	private void registerBuiltCommand(
			@NotNull Command.Builder<S> builder,
			@NotNull Consumer<CommandContext<S>> handler
	) {
		CommandExecutionHandler<S> executionHandler = handler::accept;
		Command<S> command = builder.handler(executionHandler).build();
		commandManager.command(command);
	}

	/**
	 * Creates a cooldown manager for command cooldowns.
	 *
	 * @return The cooldown manager
	 */
	private CooldownManager<S> createCooldownManager() {
		CooldownRepository<S> repository = CooldownRepository.mapping(
				uuidExtractor,
				CooldownRepository.forMap(new HashMap<>())
		);

		ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
		CooldownConfiguration<S> configuration = CooldownConfiguration.<S>builder()
				.repository(repository)
				.addCreationListener(new ScheduledCleanupCreationListener<>(executorService, repository))
				.build();

		return CooldownManager.cooldownManager(configuration);
	}

	@Override
	public void setRootCommand(@NotNull String rootCommandName) {
		this.rootCommand = rootCommandName;
	}

	@Override
	@Nullable
	public String getRootCommand() {
		return rootCommand;
	}

	@Override
	@NotNull
	public CommandManager<S> getCommandManager() {
		return commandManager;
	}

	private record UsageArgument(String name, boolean required, boolean greedy) {}
}

